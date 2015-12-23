package de.codesourcery.spring.contextrewrite;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.commons.lang3.Validate;

import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.ContextConfiguration;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.InsertAttributeRule;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.InsertElementRule;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.RemoveRule;
import de.codesourcery.spring.contextrewrite.ContextRewritingBootStrapper.ReplaceRule;

/**
 * Populates a <code>RewriteConfig</code> by gathering XML rewrite annotations from a class hierarchy. 
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class AnnotationParser 
{
    /**
     * Parse rewrite annotations.
     * 
     * @param clazz class whose annotations should be parsed. This method will also parse parent classes and merge any rewriting annotations it encounters 
     *             according to the rules described in {@link RewriteConfig#getRules()}.
     *               
     * @return rewriting configuration
     * @throws NoSuchElementException if neither the input class nor any of its parents had a {@link ContextConfiguration} annotation.
     */
    public RewriteConfig parse(Class<?> clazz) throws NoSuchElementException
    {
        Validate.notNull(clazz, "clazz must not be NULL");
        
        RewriteConfig first = null;
        RewriteConfig previous = null;
        Class<?> currentClass = clazz;
        while ( currentClass != Object.class ) 
        {
            final Optional<ContextConfiguration> ctxConfiguration = Optional.ofNullable( currentClass.getAnnotation( ContextConfiguration.class ) );
            
            final RewriteConfig config = new RewriteConfig( currentClass );

            if ( ctxConfiguration.isPresent() ) {
                config.setContextPath( ctxConfiguration.get().value() );
                config.setDebug( ctxConfiguration.get().debug() );
                config.setDumpXML( ctxConfiguration.get().dumpRewrittenXML() );
            }
            
            config.addRules( XMLRewrite.wrap( currentClass.getAnnotationsByType( ReplaceRule.class ) ) );
            config.addRules( XMLRewrite.wrap( currentClass.getAnnotationsByType( RemoveRule.class ) ) );
            config.addRules( XMLRewrite.wrap( currentClass.getAnnotationsByType( InsertElementRule.class ) ) );
            config.addRules( XMLRewrite.wrap( currentClass.getAnnotationsByType( InsertAttributeRule.class ) ) );

            if ( ctxConfiguration.isPresent() || config.hasRules() ) 
            {
                if ( previous != null ) 
                {
                    previous.setParent( config );
                }
                previous = config;

                if ( first == null ) {
                    first = config;
                }                
            }
            currentClass = currentClass.getSuperclass();
        }
        if ( first == null ) {
            throw new NoSuchElementException("Found no @"+ContextConfiguration.class.getName()+" annotation on "+clazz.getName()+" or any of its super classes");             
        }
        return first;
    }
}
