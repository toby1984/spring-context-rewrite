package de.codesourcery.spring.contextrewrite;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.Resource;

import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.ContextConfiguration;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.RemoveRule;

public class XMLRewriteTest 
{
    @ContextConfiguration(value="/parent.xml")
    @RemoveRule(xpath="/beans/bean[@id='bean1']")
    public static final class TestMergeNamespaces {
    }
    
    @Test
    public void testSchemaLocationsAndNameSpacesGetMerged() throws Exception {
        
        final RewriteConfig config = new AnnotationParser().parse( TestMergeNamespaces.class );
        final XMLRewrite rewrite = new XMLRewrite();
        final Resource filtered = rewrite.filterResource( config.getResource() , config );
        final String transformed = XMLRewrite.stripXML( XMLRewrite.readXMLString( filtered ) );
        Assert.assertEquals( "<beans xmlns=\"http://www.springframework.org/schema/beans\" xmlns:tx=\"http://www.springframework.org/schema/tx\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.springframework.org/schema/beans         http://www.springframework.org/schema/beans/spring-beans.xsd http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx.xsd\"/>" , transformed );
    }
}
