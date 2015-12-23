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
package de.codesourcery.spring.contextrewrite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
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
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.InsertAttributeRule;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.InsertElementRule;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.RemoveRule;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.ReplaceRule;

/**
 * Helper class that performs the actual XML parsing and rewriting.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class XMLRewrite 
{
    private boolean debugEnabled = false;

    protected static abstract class Rule
    {
        public final String xpath;
        public final String id;

        public boolean matched;


        public Rule(String xpath,String id)
        {
            if ( StringUtils.isBlank( xpath ) ) {
                throw new IllegalArgumentException("xpath expression must not be NULL/blank");
            }
            this.xpath = xpath;
            this.id = id;
        }

        public final boolean isIDSet() 
        {
            return id != null && ! ContextRewritingBootStrapper.NULL_STRING.equals( id );
        }

        public final boolean isIDNotSet() {
            return id == null ||  ContextRewritingBootStrapper.NULL_STRING.equals( id );
        }

        public abstract void apply(Document document,Node matchedNode) throws Exception;

        public boolean hasID(String id) 
        {
            Validate.notNull(id, "id must not be NULL");
            return Objects.equals( this.id , id );
        }
    }

    private static Rule wrap(ReplaceRule r)
    {
        final String newValue;

        if ( ! ContextRewritingBootStrapper.NULL_STRING .equals( r.replacement() ) )
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
        return new Rule( r.xpath() , r.id() )
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

    /**
     * Converts an array of <code>ReplaceRule</code> annotations into the corresponding rewriting rules.
     * 
     * @param rules
     * @return
     */      
    public static List<Rule> wrap(ReplaceRule[] rules)
    {
        Validate.notNull(rules, "rules must not be NULL");
        return Stream.of( rules ).map( XMLRewrite::wrap ).collect( Collectors.toCollection( ArrayList::new ) );
    }

    /**
     * Converts an array of <code>RemoveRule</code> annotations into the corresponding rewriting rules.
     * 
     * @param rules
     * @return
     */    
    public static List<Rule> wrap(RemoveRule[] rules)
    {
        Validate.notNull(rules, "rules must not be NULL");
        return Stream.of( rules ).map( XMLRewrite::wrap ).collect( Collectors.toCollection( ArrayList::new ) );
    }

    /**
     * Converts an array of <code>InsertElementRule</code> annotations into the corresponding rewriting rules.
     * 
     * @param rules
     * @return
     */
    public static List<Rule> wrap(InsertElementRule[] rules)
    {
        Validate.notNull(rules, "rules must not be NULL");
        return Stream.of( rules ).map( XMLRewrite::wrap ).collect( Collectors.toCollection( ArrayList::new ) );
    }

    /**
     * Converts an array of <code>InsertAttributeRule</code> annotations into the corresponding rewriting rules.
     * 
     * @param rules
     * @return
     */
    public static List<Rule> wrap(InsertAttributeRule[] rules)
    {
        Validate.notNull(rules, "rules must not be NULL");
        return Stream.of( rules ).map( XMLRewrite::wrap ).collect( Collectors.toCollection( ArrayList::new ) );
    }

    private static Rule wrap(RemoveRule r)
    {
        return new Rule( r.xpath() , r.id() )
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

    private static Rule wrap(InsertElementRule r)
    {
        return new Rule( r.xpath() , r.id() )
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

    private static Rule wrap(InsertAttributeRule r)
    {
        return new Rule( r.xpath() , r.id() )
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

    protected static Document parseXMLFragment(String xml) throws ParserConfigurationException, SAXException, IOException
    {
        final String replacement = "<?xml version=\"1.0\" ?>"+xml;
        return parseXML( new ByteArrayInputStream( replacement.getBytes() ) );
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


    protected void rewriteXML(Document doc,List<Rule> rules,boolean failOnUnmatchedRule,boolean onlyUnmatchedRules) throws Exception
    {
        for ( Rule r : rules )
        {
            if ( onlyUnmatchedRules && r.matched ) {
                continue;
            }
            
            final List<Node> nodes = evaluateXPath( r.xpath , doc );

            if ( debugEnabled ) {
                debug("RULE MATCHED "+nodes.size()+" nodes: "+r);
            }
            if ( nodes.size() > 0 ) {
                r.matched = true;
            }

            for ( Node child : nodes )
            {
                r.apply( doc , child );
            }
        }

        final List<Rule> unmatched = new ArrayList<>( rules );
        unmatched.removeIf( r -> r.matched );

        if ( failOnUnmatchedRule && ! unmatched.isEmpty() ) 
        {
            unmatched.forEach( r -> System.err.println("ERROR: Unmatched rule "+r) );
            throw new RuntimeException("One or more rules were not matched");
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

    protected final class Pair
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

    protected static byte[] toByteArray(Document doc,boolean prettyPrint) throws TransformerException, XPathExpressionException
    {
        final TransformerFactory transformerFactory = TransformerFactory.newInstance();
        final Transformer transformer = transformerFactory.newTransformer();
        if ( prettyPrint ) {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            doc.getDocumentElement().normalize();
            XPathExpression xpath = XPathFactory.newInstance().newXPath().compile("//text()[normalize-space(.) = '']");
            NodeList blankTextNodes = (NodeList) xpath.evaluate(doc, XPathConstants.NODESET);
            for (int i = 0; i < blankTextNodes.getLength(); i++) {
                blankTextNodes.item(i).getParentNode().removeChild(blankTextNodes.item(i));
            }            
        }
        final DOMSource source = new DOMSource(doc);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final StreamResult result = new StreamResult( out );
        transformer.transform(source, result);
        return out.toByteArray();
    }    

    protected List<Node> evaluateXPath(String xpathExpression,Node node) throws XPathExpressionException
    {
        final XPathFactory factory = XPathFactory.newInstance();
        final XPath xpath = factory.newXPath();
        final XPathExpression pathExpression = xpath.compile( xpathExpression );
        return wrapNodeList( (NodeList) pathExpression.evaluate(node,XPathConstants.NODESET) );
    }

    protected static Document parseXML(InputStream in) throws ParserConfigurationException, SAXException, IOException
    {
        Validate.notNull(in, "in must not be NULL");
        try 
        {
            final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            return dBuilder.parse( in );
        } 
        finally 
        {
            try {
                in.close();
            } catch(Exception e) { /* ok */ }
        }
    }

    private void debug(String msg)
    {
        if ( debugEnabled ) {
            System.out.println("DEBUG: "+msg);
        }
    }    

    private Document parseXML(Resource resource,List<Rule> rules) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        debug("Now loading "+resource);

        try ( InputStream in = resource.getInputStream() )
        {
            final Document doc = XMLRewrite.parseXML( in );
            rewriteXML( doc , rules , false , false );

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
                final Document importedXML = parseXML( imported , rules );
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

    /**
     * Transforms XML according to a given <code>RewriteConfig</code>.
     *  
     * @param resource
     * @param config
     * @return Resource that provides the transformed XML.
     * @throws Exception
     */
    public Resource filterResource(Resource resource, RewriteConfig config) throws Exception 
    {
        Validate.notNull(resource, "resource must not be NULL");
        Validate.notNull(config, "config must not be NULL");

        final boolean dumpRewrittenXML = config.isDumpXML();
        this.debugEnabled = config.isDebug();
        
        final List<Rule> rules = config.getRules();

        // parse XML
        final Document doc = parseXML( resource , rules );

        // rewrite XML
        rewriteXML( doc , rules , true , true );

        // write XML to byte[] array
        final byte[] data = toByteArray( doc , false );

        if ( dumpRewrittenXML )
        {
            final String[] lines = new String(data).split("\n");
            int no = 1;
            for ( String line : lines )
            {
                System.out.println( no+":   "+line);
                no++;
            }
        }
        return new AbstractResource()
        {
            @Override
            public String getDescription() {
                return "Spring XML filtered from "+resource;
            }

            @Override
            public InputStream getInputStream() throws IOException
            {
                return new ByteArrayInputStream( data  );
            }
        };            
    }

    protected static String readXMLString(Resource resource) throws IOException, ParserConfigurationException, SAXException, TransformerException, XPathExpressionException 
    {
        final Document doc = parseXML( resource.getInputStream() );
        byte[] data = XMLRewrite.toByteArray(doc,true);
        return new String( data );
    }
    
    protected static String stripXML(String xml) 
    {
        final String header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>";
        if ( xml != null && xml.startsWith( header ) ) {
            xml = xml.substring( header.length() );
        }
        if ( xml != null ) {
            xml = xml.replace("\n","");
        }
        return xml;
    }
}
