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

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
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
import org.xml.sax.InputSource;

/**
 * Spring {@link TestContextBootstrapper} that provides annotation support for rewriting the Spring XML using XPath expressions.
 *
 * Example Usage:
 * {@code
 {@literal @}BootstrapWith(value=ContextRewritingBootStrapper.class)
 {@literal @}ContextRewritingBootStrapper.ContextConfiguration(value="/spring-auth-test.xml",dumpRewrittenXML=true)
 {@literal @}ReplaceRule( xpath="/beans/bean[{@literal @}id='settingsResource']/constructor-arg/{@literal @}value" , replacement ="/some.properties" )
 {@literal @}RemoveRule( xpath="/beans/bean[{@literal @}id='settingsResource']/constructor-arg" )
 {@literal @}InsertRule( xpath="/beans/bean[{@literal @}id='settingsResource']" , insert ="<constructor-arg value="other.properties" )
 public class SpringIntegrationTest extends AbstractTransactionalJUnit4SpringContextTests
 {
     {@literal @}Test
     public void testDoNothing() {
     }
 }
 * }
 * Note that rules get executed in order they are listed.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class ContextRewritingBootStrapper extends DefaultTestContextBootstrapper
{
    /**
     * Hack used to fake 'null' / 'not-set' default values for annotation attributes. 
     */
    public static final String NULL_STRING = "THIS STRING IS ABSENT";
    
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
        public static final Class<?> NULL_CLASS = Void.class;

        public String xpath();
        public String id() default NULL_STRING; // annotations cannot have NULL as default value ... god knows why...
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
        public String id() default NULL_STRING; // annotations cannot have NULL as default value ... god knows why...        
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
        public String id() default NULL_STRING; // annotations cannot have NULL as default value ... god knows why...        
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
        public String id() default NULL_STRING; // annotations cannot have NULL as default value ... god knows why...        
        public String name();
        public String value();
    }

    @Override
    public void setBootstrapContext(final BootstrapContext ctx)
    {
        final RewriteConfig config = new AnnotationParser().parse( ctx.getTestClass() );
        
        final XMLRewrite rewrite = new XMLRewrite();
        
        super.setBootstrapContext( new BootstrapContext() {

            @Override
            public Class<?> getTestClass() {
                return ctx.getTestClass();
            }

            @Override
            public CacheAwareContextLoaderDelegate getCacheAwareContextLoaderDelegate()
            {
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
                                                try 
                                                {
                                                    return super.loadBeanDefinitions( new EncodedResource( rewrite.filterResource( config.getResource() , config ) ) );
                                                }
                                                catch (Exception e)
                                                {
                                                    throw new BeanDefinitionStoreException("Failed to load from classpath:"+config.getContextPath() , e);
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
}