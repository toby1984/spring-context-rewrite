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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.NoSuchElementException;

import org.apache.commons.lang3.Validate;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.ContextConfiguration;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.InsertAttributeRule;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.InsertElementRule;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.RemoveRule;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.ReplaceRule;

public class AnnotationParserTest 
{
    private AnnotationParser parser;
    
    /* === start: test classes === */
    
    public static final class UnannotatedClass {}
    
    @ContextConfiguration(value="/test.xml",debug=true)
    public static final class ClassWithDebugEnabled { }
    
    @ContextConfiguration(value="/test.xml",dumpRewrittenXML=true)
    public static final class ClassWithDumpXMLEnabled { }    
    
    @ContextConfiguration(value="/test.xml")
    public static class ClassA { }     

    @RemoveRule(id="rule1",xpath="/beans/bean")    
    public static final class InheritContextConfiguration extends ClassA { }     
    
    @RemoveRule(id="rule1",xpath="/beans/bean")    
    public static class ClassC { }     

    @ContextConfiguration(value="/test.xml")
    public static final class InheritRuleWithNoID extends ClassC { }       
    
    @ReplaceRule(id="rule1",xpath="/beans/bean" , replacement="<bean1/>")    
    public static class ClassD { }     

    @ContextConfiguration(value="/test.xml")
    @ReplaceRule(id="rule1",xpath="/beans/bean" , replacement="<bean2/>")    
    public static final class InheritRuleWithSameID extends ClassD { }       
    
    @ContextConfiguration(value="/test.xml")
    @RemoveRule(id="rule1",xpath="/beans/bean")
    @RemoveRule(id="rule1",xpath="/beans/bean")
    public static final class ClassWithDuplicateRuleIDs{ }    
    
    @ContextConfiguration(value="")
    public static final class ClassWithBlankContext { }
    
    @ContextConfiguration(value="/test.xml")
    public static final class ClassWithNoRules { }    
    
    @ContextConfiguration(value="/test.xml")
    @RemoveRule(xpath="/beans/bean")
    public static final class ClassWithOneRemoveRule { }
    
    @ContextConfiguration(value="/test.xml")
    @RemoveRule(xpath="/beans/bean1")
    @RemoveRule(xpath="/beans/bean2")
    public static final class ClassWithTwoRemoveRules { }      
    
    @ContextConfiguration(value="/test.xml")
    @InsertElementRule(xpath="/beans",insert="<bean1/>" )
    public static final class ClassWithOneInsertElementRule { }
    
    @ContextConfiguration(value="/test.xml")
    @InsertElementRule(xpath="/beans",insert="<bean1/>" )
    @InsertElementRule(xpath="/beans",insert="<bean2/>" )
    public static final class ClassWithTwoInsertElementRules { }    
    
    @ContextConfiguration(value="/test.xml")
    @InsertAttributeRule(xpath="/beans/bean", name = "key1", value = "value1" )
    public static final class ClassWithOneInsertAttributeRule { }    
    
    @ContextConfiguration(value="/test.xml")
    @InsertAttributeRule(xpath="/beans/bean", name = "key1", value = "value1" )
    @InsertAttributeRule(xpath="/beans/bean", name = "key2", value = "value2" )
    public static final class ClassWithTwoInsertAttributeRules { }
    
    @ContextConfiguration(value="/test.xml")
    @ReplaceRule(xpath="/beans/bean1", replacement="<bean2/>")
    public static final class ClassWithOneReplaceRule { }     
    
    @ContextConfiguration(value="/test.xml")
    @ReplaceRule(xpath="/beans/bean1", replacement="<bean3/>")
    @ReplaceRule(xpath="/beans/bean2", replacement="<bean4/>")
    public static final class ClassWithTwoReplaceRules { }      
    
    @ReplaceRule(id="rule1",xpath="/beans/bean1" , replacement="<bean4/>")    
    @ReplaceRule(xpath="/beans/bean2" , replacement="<bean5/>")    
    public static class ClassE { }     

    @ContextConfiguration(value="/test.xml")
    @ReplaceRule(id="rule2",xpath="/beans/bean3" , replacement="<bean6/>")    
    public static final class InheritRulesWithAndWithoutID1 extends ClassE { }      
    
    @ReplaceRule(id="rule2",xpath="/beans/bean3" , replacement="<bean6/>")  
    public static class ClassF { }     

    @ContextConfiguration(value="/test.xml")
    @ReplaceRule(id="rule1",xpath="/beans/bean1" , replacement="<bean4/>")    
    @ReplaceRule(xpath="/beans/bean2" , replacement="<bean5/>")   
    public static final class InheritRulesWithAndWithoutID2 extends ClassF { }      
    
    /* === end: test classes === */    
    
    @Before
    public void setup() {
        parser = new AnnotationParser();
    }
    
    @Test(expected=NoSuchElementException.class)
    public void testParsingClassWithNoAnnotationsFails() {
        parser.parse( UnannotatedClass.class );
    }
    
    @Test(expected=NullPointerException.class)
    public void testParsingNullClassFails() {
        parser.parse( null );
    } 
    
    @Test(expected=IllegalArgumentException.class)
    public void testParsingClassWithBlankContextFails() {
        parser.parse( ClassWithBlankContext.class );
    }     
    
    @Test
    public void testParsingClassWithNoRulesWorks()
    {
        final RewriteConfig config = parser.parse( ClassWithNoRules.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertTrue( config.hasNoRules() );
        assertFalse( config.hasRules() );
        assertTrue( config.getRules().isEmpty() );
    }   
    
    @Test
    public void testParsingClassWithOneRemoveRuleWorks() throws Exception
    {
        final RewriteConfig config = parser.parse( ClassWithOneRemoveRule.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 1 , config.getRules().size() );
        
        assertThat("<beans><bean/></beans>").with( config ).transformsTo( "<beans/>" );
    }   
    
    @Test
    public void testParsingClassWithTwoRemoveRuleWorks() throws Exception
    {
        final RewriteConfig config = parser.parse( ClassWithTwoRemoveRules.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 2 , config.getRules().size() );
        
        assertThat("<beans><bean1/><bean2/></beans>").with( config ).transformsTo( "<beans/>" );
    }      
    
    @Test
    public void testParsingClassWithOneInsertElementRuleWorks() throws Exception
    {
        final RewriteConfig config = parser.parse( ClassWithOneInsertElementRule.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 1 , config.getRules().size() );
        
        assertThat("<beans/>").with( config ).transformsTo( "<beans><bean1/></beans>" );
    }       
    
    @Test
    public void testParsingClassWithTwoInsertElementRulesWorks() throws Exception
    {
        final RewriteConfig config = parser.parse( ClassWithTwoInsertElementRules.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 2 , config.getRules().size() );
        
        assertThat("<beans/>").with( config ).transformsTo( "<beans><bean1/><bean2/></beans>" );
    }   
    
    @Test
    public void testParsingClassWithOneInsertAttributeRuleWorks() throws Exception
    {
        final RewriteConfig config = parser.parse( ClassWithOneInsertAttributeRule.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 1 , config.getRules().size() );
        
        assertThat("<beans><bean/></beans>").with( config ).transformsTo( "<beans><bean key1=\"value1\"/></beans>" );
    }     
    
    @Test
    public void testParsingClassWithOneReplaceRuleWorks() throws Exception
    {
        final RewriteConfig config = parser.parse( ClassWithOneReplaceRule.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 1 , config.getRules().size() );
        
        assertThat("<beans><bean1/></beans>").with( config ).transformsTo( "<beans><bean2/></beans>" );
    }     
    
    @Test
    public void testParsingClassWithTwoReplaceRulesWorks() throws Exception
    {
        final RewriteConfig config = parser.parse( ClassWithTwoReplaceRules.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 2 , config.getRules().size() );
        
        assertThat("<beans><bean1/><bean2/></beans>").with( config ).transformsTo( "<beans><bean3/><bean4/></beans>" );
    }     
    
    @Test
    public void testParsingClassWithDebugEnabledWorks() throws Exception
    {
        final RewriteConfig config = parser.parse( ClassWithDebugEnabled.class );
        assertNotNull( config );
        assertTrue( config.isDebug() );
        assertFalse( config.isDumpXML() );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertTrue( config.hasNoRules() );
        assertFalse( config.hasRules() );
        assertEquals( 0 , config.getRules().size() );
    }    
    
    @Test
    public void testParsingClassWithDumpXMLEnabledWorks() throws Exception
    {
        final RewriteConfig config = parser.parse( ClassWithDumpXMLEnabled.class );
        assertNotNull( config );
        assertFalse( config.isDebug() );
        assertTrue( config.isDumpXML() );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertTrue( config.hasNoRules() );
        assertFalse( config.hasRules() );
        assertEquals( 0 , config.getRules().size() );
    }     
    
    @Test(expected=IllegalStateException.class)
    public void testParsingClassWithDuplicateRuleIDsFails() {
        parser.parse( ClassWithDuplicateRuleIDs.class );
    }
    
    @Test
    public void testInheritContextConfiguration() throws Exception
    {
        final RewriteConfig config = parser.parse( InheritContextConfiguration.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 1 , config.getRules().size() );
        
        assertThat("<beans><bean/></beans>").with( config ).transformsTo( "<beans/>" );
    } 
    
    @Test
    public void testInheritRuleWithNoID() throws Exception
    {
        final RewriteConfig config = parser.parse( InheritRuleWithNoID.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 1 , config.getRules().size() );
        
        assertThat("<beans><bean/></beans>").with( config ).transformsTo( "<beans/>" );
    }   
    
    @Test
    public void testInheritRuleWithSameIDOverrides() throws Exception
    {
        final RewriteConfig config = parser.parse( InheritRuleWithSameID.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 1 , config.getRules().size() );
        
        assertThat("<beans><bean/></beans>").with( config ).transformsTo( "<beans><bean2/></beans>" );
    }      
    
    @Test
    public void testInheritRulesWithAndWithoutID1() throws Exception
    {
        final RewriteConfig config = parser.parse( InheritRulesWithAndWithoutID1.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 3 , config.getRules().size() );
        
        assertThat("<beans><bean1/><bean2/><bean3/></beans>").with( config ).transformsTo( "<beans><bean4/><bean5/><bean6/></beans>" );
    }  
    
    @Test
    public void testInheritRulesWithAndWithoutID2() throws Exception
    {
        final RewriteConfig config = parser.parse( InheritRulesWithAndWithoutID2.class );
        assertNotNull( config );
        assertEquals( "/test.xml" , config.getContextPath() );
        assertFalse( config.hasNoRules() );
        assertTrue( config.hasRules() );
        assertEquals( 3 , config.getRules().size() );
        
        assertThat("<beans><bean1/><bean2/><bean3/></beans>").with( config ).transformsTo( "<beans><bean4/><bean5/><bean6/></beans>" );
    }     
    
    // == helper methods ==
    
    private TransformHelper assertThat(String xml) {
        return new TransformHelper(xml);
    }
    
    protected static final class TransformHelper {
        
        private final String inputXML;
        private RewriteConfig config;
        
        public TransformHelper(String inputXML) {
            Validate.notBlank(inputXML, "inputXML must not be NULL or blank");
            this.inputXML = inputXML;
        }
        
        public TransformHelper with(RewriteConfig config) {
            this.config = config;
            return this;
        }
        
        public void transformsTo(String expected ) throws Exception {
            assertTransform( inputXML , config , expected );
        }
    }
    
    private static void assertTransform(String xml,RewriteConfig config,String expected) throws Exception 
    {
        final XMLRewrite rewrite = new XMLRewrite();
        final Resource filtered = rewrite.filterResource( new InputStreamResource( new ByteArrayInputStream( xml.getBytes() ) ) , config );
        
        final String rewritten = XMLRewrite.stripXML( XMLRewrite.readXMLString( filtered ) );
        assertEquals( expected , rewritten );
    }
}