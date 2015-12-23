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
