/**
 * Copyright 2015 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voipfuture.cloudportal.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.BootstrapContext;
import org.springframework.test.context.CacheAwareContextLoaderDelegate;
import org.springframework.test.context.ContextLoader;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextBootstrapper;
import org.springframework.test.context.support.AbstractGenericContextLoader;
import org.springframework.test.context.support.DefaultTestContextBootstrapper;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Spring {@link TestContextBootstrapper} that provides annotation support for rewriting the Spring XML using XPath expressions.
 *
 * <p>Example Usage:
 * <pre>
 * <code>
 * {@literal @}BootstrapWith(value=ContextRewritingBootStrapper.class)
 * {@literal @}ContextRewritingBootStrapper.ContextConfiguration(value="/spring-auth-test.xml",dumpRewrittenXML=true)
 * {@literal @}ReplaceRule( xpath="/beans/bean[@id='settingsResource']/constructor-arg/@value" , replacement ="/some.properties" )
 * {@literal @}RemoveRule( xpath="/beans/bean[@id='settingsResource']/constructor-arg" )
 * {@literal @}InsertRule( xpath="/beans/bean[@id='settingsResource']" , insert ="<constructor-arg value=\"other.properties\"/>" )
 * public class SpringIntegrationTest extends AbstractTransactionalJUnit4SpringContextTests
 * {
 *     {@literal @}Test
 *     public void testDoNothing() {
 *     }
 * }</code>
 * </pre>
 * Note that rules get executed in order they are listed.
 * </p>
 */
public class ContextRewritingBootStrapper extends DefaultTestContextBootstrapper
{
    private static final ThreadLocal<Boolean> DEBUG_ENABLED = new ThreadLocal<Boolean>()
    {
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    @Target(value={ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface ContextConfiguration
    {
        public String value();
        public boolean dumpRewrittenXML() default false;
        public boolean debug() default false;
    }

    /*
     * Replace
     */

    @Target(value={ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface ReplaceRules
    {
        public ReplaceRule[] value();
    }

    @Target(value={ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(ReplaceRules.class)
    public static @interface ReplaceRule
    {
        public static final String NULL_STRING = "THIS STRING IS ABSENT";
        public static final Class<?> NULL_CLASS = Void.class;

        public String xpath();
        public String replacement() default NULL_STRING; // annotations cannot have NULL as default value ... god knows why...
        public Class<?> replacementClassName() default Void.class;// annotations cannot have NULL as default value ... god knows why...
    }

    /*
     * Remove
     */

    @Target(value={ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface RemoveRules
    {
        public RemoveRule[] value();
    }

    @Target(value={ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(RemoveRules.class)
    public static @interface RemoveRule
    {
        public String xpath();
    }

    /*
     * INSERT ELEMENT
     */
    @Target(value={ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface InsertElementRules
    {
        public InsertElementRule[] value();
    }

    @Target(value={ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(InsertElementRules.class)
    public static @interface InsertElementRule
    {
        public String xpath();
        public String insert();
    }

    /*
     * INSERT ATTRIBUTE
     */
    @Target(value={ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface InsertAttributeRules
    {
        public InsertAttributeRule[] value();
    }

    @Target(value={ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(InsertAttributeRules.class)
    public static @interface InsertAttributeRule
    {
        public String xpath();
        public String name();
        public String value();
    }

    protected abstract class Rule
    {
        public final String xpath;

        public Rule(String xpath)
        {
            if ( StringUtils.isBlank( xpath ) ) {
                throw new IllegalArgumentException("xpath expression must not be NULL/blank");
            }
            this.xpath = xpath;
        }

        public abstract void apply(Document document,Node matchedNode) throws Exception;
    }

    private Rule wrap(ReplaceRule r)
    {
        final String newValue;

        if ( ! ReplaceRule.NULL_STRING .equals( r.replacement() ) )
        {
            if ( r.replacementClassName() != ReplaceRule.NULL_CLASS ) {
                throw new RuntimeException("Either replacement or replacementClassName needs to be set");
            }
            newValue = r.replacement();
        }
        else if ( r.replacementClassName() != ReplaceRule.NULL_CLASS ) {
            newValue = r.replacementClassName().getName();
        }
        else
        {
            throw new RuntimeException("You need to provide EITHER 'replacement' OR 'replacementClassName' attributes");
        }
        return new Rule( r.xpath() )
        {
            public void apply(Document document,Node matchedNode) throws Exception
            {
                switch( matchedNode.getNodeType() )
                {
                    case Node.ATTRIBUTE_NODE:
                        matchedNode.setNodeValue( newValue );
                        break;
                    case Node.ELEMENT_NODE:
                        final Document replDocument = parseXMLFragment( newValue );
                        final Node firstChild = replDocument.getFirstChild();
                        final Node adoptedNode = document.importNode( firstChild , true );
                        matchedNode.getParentNode().replaceChild( adoptedNode , matchedNode );
                        break;
                    default:
                        throw new RuntimeException();
                }
            }

            @Override
            public String toString() {
                return "REPLACE: "+r.xpath()+" with '"+newValue;
            }
        };
    }

    private List<Rule> wrap(ReplaceRule[] rules)
    {
        return Stream.of( rules ).map( this::wrap ).collect( Collectors.toCollection( ArrayList::new ) );
    }

    private List<Rule> wrap(RemoveRule[] rules)
    {
        return Stream.of( rules ).map( this::wrap ).collect( Collectors.toCollection( ArrayList::new ) );
    }

    private List<Rule> wrap(InsertElementRule[] rules)
    {
        return Stream.of( rules ).map( this::wrap ).collect( Collectors.toCollection( ArrayList::new ) );
    }

    private List<Rule> wrap(InsertAttributeRule[] rules)
    {
        return Stream.of( rules ).map( this::wrap ).collect( Collectors.toCollection( ArrayList::new ) );
    }

    private Rule wrap(RemoveRule r)
    {
        return new Rule( r.xpath() )
        {
            @Override
            public void apply(Document document, Node matchedNode) throws Exception {
                matchedNode.getParentNode().removeChild( matchedNode );
            }

            @Override
            public String toString() {
                return "REMOVE: "+r.xpath();
            }
        };
    }

    private Rule wrap(InsertElementRule r)
    {
        return new Rule( r.xpath() )
        {
            @Override
            public void apply(Document document, Node matchedNode) throws Exception {
                final Document newDocument = parseXMLFragment( r.insert() );
                final Node firstChild = newDocument.getFirstChild();
                final Node adoptedNode = document.importNode( firstChild , true );
                matchedNode.appendChild( adoptedNode );
            }

            @Override
            public String toString() {
                return "INSERT ELEMENT: "+r.xpath()+" with '"+r.insert();
            }
        };
    }

    private Rule wrap(InsertAttributeRule r)
    {
        return new Rule( r.xpath() )
        {
            @Override
            public void apply(Document document, Node matchedNode) throws Exception
            {
                final Attr attribute = document.createAttribute( r.name() );
                attribute.setValue( r.value() );
                matchedNode.getAttributes().setNamedItem( attribute );
            }

            @Override
            public String toString() {
                return "INSERT ATTRIBUTE: "+r.xpath()+" with "+r.name()+"="+r.value();
            }
        };
    }

    private void debug(String msg)
    {
        if ( DEBUG_ENABLED.get().booleanValue() ) {
            System.out.println("DEBUG: "+msg);
        }
    }

    @Override
    public void setBootstrapContext(final BootstrapContext ctx)
    {
        debug("Setting bootstrap context");

        final ContextConfiguration ctxAnnotation = Stream.of( ctx.getTestClass().getAnnotations() )
                .filter( annot -> annot instanceof ContextConfiguration )
                .map( annot -> (ContextConfiguration) annot)
                .findFirst()
                .orElseThrow( () -> new RuntimeException("Expected one "+ContextRewritingBootStrapper.ContextConfiguration.class.getSimpleName()+" annotation on "+
                        ctx.getTestClass().getName()+" but found none") );

        DEBUG_ENABLED.set( ctxAnnotation.debug() );

        final List<Rule> rules1 = wrap( ctx.getTestClass().getAnnotationsByType( ReplaceRule.class ) );
        final List<Rule> rules2 = wrap( ctx.getTestClass().getAnnotationsByType( RemoveRule.class ) );
        final List<Rule> rules3 = wrap( ctx.getTestClass().getAnnotationsByType( InsertElementRule.class ) );
        final List<Rule> rules4 = wrap( ctx.getTestClass().getAnnotationsByType( InsertAttributeRule.class ) );

        final List<Rule> rules = new ArrayList<>();
        rules.addAll( rules1 );
        rules.addAll( rules2 );
        rules.addAll( rules3 );
        rules.addAll( rules4 );
        super.setBootstrapContext( new BootstrapContext() {

            @Override
            public Class<?> getTestClass() {
                return ctx.getTestClass();
            }

            @Override
            public CacheAwareContextLoaderDelegate getCacheAwareContextLoaderDelegate()
            {
                debug("getCacheAwareContextLoaderDelegate");
                return new CacheAwareContextLoaderDelegate() {

                    @Override
                    public ApplicationContext loadContext(MergedContextConfiguration mergedContextConfiguration)
                    {
                        final MergedContextConfiguration wrapper = new MergedContextConfiguration(mergedContextConfiguration)
                        {
                            @Override
                            public ContextLoader getContextLoader()
                            {
                                return new AbstractGenericContextLoader() {

                                    @Override
                                    protected BeanDefinitionReader createBeanDefinitionReader(GenericApplicationContext context)
                                    {
                                        final XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader( context )
                                        {
                                            private int loadBeanDefinitions()
                                            {
                                                try {
                                                    return super.loadBeanDefinitions( new EncodedResource( getFilteredClasspathResource( ctxAnnotation , rules ) ) );
                                                }
                                                catch (Exception e)
                                                {
                                                    throw new BeanDefinitionStoreException("Failed to load from classpath:"+ctxAnnotation.value() , e);
                                                }
                                            }

                                            @Override
                                            public int loadBeanDefinitions(String location) throws BeanDefinitionStoreException
                                            {
                                                return loadBeanDefinitions( (EncodedResource) null );
                                            }

                                            @Override
                                            public int loadBeanDefinitions(String location, Set<Resource> actualResources) throws BeanDefinitionStoreException
                                            {
                                                if ( StringUtils.isBlank( location ) ) {
                                                    return loadBeanDefinitions();
                                                }
                                                return super.loadBeanDefinitions( location , actualResources );
                                            }

                                            @Override
                                            public int loadBeanDefinitions(String... locations) throws BeanDefinitionStoreException {
                                                return loadBeanDefinitions();
                                            }

                                            public int loadBeanDefinitions(InputSource inputSource) throws BeanDefinitionStoreException
                                            {
                                                return loadBeanDefinitions();
                                            }

                                            public int loadBeanDefinitions(InputSource inputSource, String resourceDescription) throws BeanDefinitionStoreException {
                                                return loadBeanDefinitions();
                                            }
                                        };
                                        return reader;
                                    }

                                    @Override
                                    protected String getResourceSuffix() {
                                        return ".xml";
                                    }
                                };
                            }
                        };
                        return ctx.getCacheAwareContextLoaderDelegate().loadContext( wrapper );
                    }

                    @Override
                    public void closeContext(MergedContextConfiguration mergedContextConfiguration, HierarchyMode hierarchyMode)
                    {
ctx.getCacheAwareContextLoaderDelegate().closeContext(mergedContextConfiguration, hierarchyMode);
                    }
                };
            }
        });
    }

    protected List<Node> evaluateXPath(String xpathExpression,Node node) throws XPathExpressionException
    {
        final XPathFactory factory = XPathFactory.newInstance();
        final XPath xpath = factory.newXPath();
        final XPathExpression pathExpression = xpath.compile( xpathExpression );
        return wrapNodeList( (NodeList) pathExpression.evaluate(node,XPathConstants.NODESET) );
    }

    protected Document parseXML(InputStream in) throws ParserConfigurationException, SAXException, IOException
    {
        final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.parse( in );
    }

    protected Document parseXML(Resource resource) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        debug("Now loading "+resource);

        try ( InputStream in = resource.getInputStream() )
        {
            final Document doc = parseXML( in );
            final List<Node> importNodes = evaluateXPath( "/beans//import", doc);
            debug("Found "+importNodes.size()+" import statements");
            for ( Node importNode : importNodes )
            {
                debug("(1) Node has parent: "+importNode.getParentNode());
                String path = importNode.getAttributes().getNamedItem("resource").getNodeValue();
                if ( path.startsWith("classpath:" ) ) {
                    path = path.substring( "classpath:".length() );
                }
                debug("Including '"+path+"' , now at "+resource);
                final Resource imported;
                if ( path.startsWith( "/" ) ) { // absolute
                    imported = new ClassPathResource( path );
                } else {
                    imported = resource.createRelative( path );
                }
                final Document importedXML = parseXML( imported );
                mergeAttributes( importedXML.getFirstChild() , doc.getFirstChild() , doc );

                final List<Node> beans = wrapNodeList( importedXML.getFirstChild().getChildNodes() );

                for ( Node beanNode : beans )
                {
                    final Node adoptedNode = doc.adoptNode( beanNode.cloneNode(true) );
                    importNode.getParentNode().insertBefore( adoptedNode , importNode );
                }
                importNode.getParentNode().removeChild( importNode );
            }
            debug("*** return ***");
            return doc;
        }
        catch(Exception e) {
            throw new RuntimeException("Failed to load XML from '"+resource+"'",e);
        }
    }

    protected static final class Pair
    {
        public final String first;
        public final String second;

        public Pair(String first, String second) {
            this.first = first;
            this.second = second;
        }

        public boolean sameFirst(Pair other) {
            return Objects.equals( this.first , other.first );
        }

        @Override
        public String toString() {
            return "("+first+","+second+")";
        }
    }

    private List<Pair> toPairs(String[] data)
    {
        final List<Pair> result = new ArrayList<>();
        for ( int i = 0 ; i < data.length ; i+=2 )
        {
            result.add( new Pair( data[i] , (i+1) < data.length ? data[i+1] : null ) );
        }
        return result;
    }

    private String[] split(String input)
    {
        if ( input == null ) {
            return new String[0];
        }
        input = input.trim();
        while(true) {
            String newValue = input.replace( "  " , " ");
            if ( newValue.equals( input ) ) {
                break;
            }
            input = newValue;
        }
        return input.split(" ");
    }

    private void mergeAttributes(Node source,Node target,Document targetDocument)
    {
        for ( int i = 0 , len = source.getAttributes().getLength() ; i < len ; i++ )
        {
            final Node attrToMerge = source.getAttributes().item( i );
            final String attrName = attrToMerge.getNodeName();
            final String attrValue = attrToMerge.getNodeValue();
            final Optional<Node> existingAttr = findAttribute( target , attrToMerge );
            if ( existingAttr.isPresent() )
            {
                final String[] existingValues = split( existingAttr.get().getNodeValue() );

                if ( attrName.endsWith(":schemaLocation" ) || attrName.equals("schemaLocation" ))
                {
                    final List<Pair> existingPairs = toPairs( existingValues );
                    final List<Pair> newPairs = toPairs( split( attrValue ) );
                    final String toAdd = newPairs.stream()
                            .filter( p -> existingPairs.stream().noneMatch( x -> x.sameFirst(p) ) )
                            .map( p -> p.first+" "+p.second ).collect( Collectors.joining(" ") );
                    if ( ! toAdd.isEmpty() )
                    {
                        existingAttr.get().setNodeValue( existingAttr.get().getNodeValue()+" "+toAdd );
                    }
                    continue;
                }

                // merge value
                if ( Stream.of( existingValues ).noneMatch( value -> value.equals( attrToMerge.getNodeValue() ) ) )
                {
                    if ( existingValues.length == 0 ) {
                        debug("adding missing attribute value "+attrName+"="+attrValue );
                        existingAttr.get().setNodeValue( attrValue );
                    } else {
                        debug("Appending existing attribute "+existingAttr.get().getNodeName()+"="+attrValue );
                        existingAttr.get().setNodeValue( existingAttr.get().getNodeValue()+" "+attrValue );
                    }
                } else {
                    debug("Already present: attribute "+existingAttr.get().getNodeName()+"="+attrValue );
                }
            }
            else
            {
                debug("Adding new attribute "+attrName+"="+attrValue );
                final Node cloned = targetDocument.adoptNode( attrToMerge.cloneNode( true ));
                target.getAttributes().setNamedItem( cloned );
            }
        }
    }

    private Optional<Node> findAttribute( Node n , Node attr)
    {
        for ( int i = 0 , len = n.getAttributes().getLength() ; i < len ; i++ )
        {
            final Node attribute = n.getAttributes().item( i );
            if ( attribute.getNodeName().equals( attr.getNodeName() ) ) {
                return Optional.of( attribute );
            }
        }
        return Optional.empty();
    }


    protected void rewriteXML(Document doc,List<Rule> rules) throws Exception
    {
        for ( Rule r : rules )
        {
            final List<Node> nodes = evaluateXPath( r.xpath , doc );

            if ( nodes.isEmpty() ) {
                throw new RuntimeException("Failed to match rule "+r);
            }
            for ( Node child : nodes )
            {
                r.apply( doc , child );
            }
        }
    }

    private List<Node> wrapNodeList(NodeList list)
    {
        final List<Node> result = new ArrayList<>( list.getLength() );
        for ( int i = 0 , len=list.getLength() ; i < len ; i++ ) {
            result.add( list.item( i ) );
        }
        return result;
    }

    protected byte[] toByteArray(Document doc) throws TransformerException
    {
        final TransformerFactory transformerFactory = TransformerFactory.newInstance();
        final Transformer transformer = transformerFactory.newTransformer();
        final DOMSource source = new DOMSource(doc);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final StreamResult result = new StreamResult( out );
        transformer.transform(source, result);
        return out.toByteArray();
    }

    protected Resource getFilteredClasspathResource(ContextConfiguration config,List<Rule> rules) throws Exception
    {
        final String classPath = config.value();
        Validate.notBlank( classPath , "classPath must not be NULL/blank");

        // parse XML
        final Document doc = parseXML( new ClassPathResource( classPath ) );

        // rewrite XML
        rewriteXML( doc , rules );

        // write XML to byte[] array
        final byte[] data = toByteArray( doc );

        if ( config.dumpRewrittenXML() )
        {
            final String[] lines = new String(data).split("\n");
            int no = 1;
            for ( String line : lines )
            {
//                System.out.println( no+":   "+line);
                System.out.println( line);
                no++;
            }
        }

        return new AbstractResource()
        {
            @Override
            public String getDescription() {
                return "Spring XML filtered from classpath:"+classPath;
            }

            @Override
            public InputStream getInputStream() throws IOException
            {
                return new ByteArrayInputStream( data  );
            }
        };
    }

    private Document parseXMLFragment(String xml) throws ParserConfigurationException, SAXException, IOException
    {
        final String replacement = "<?xml version=\"1.0\" ?>"+xml;
        return parseXML( new ByteArrayInputStream( replacement.getBytes() ) );
    }
}