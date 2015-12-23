package de.codesourcery.spring.contextrewrite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import de.codesourcery.spring.contextrewrite.XMLRewrite.Rule;

/**
 * Holds all configuration options and rules that should be applied during XML rewriting.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class RewriteConfig 
{
    private final List<Rule> anonRules = new ArrayList<>();
    private final Map<String,Rule> namedRules = new HashMap<>();
    
    private final Class<?> clazz;
    
    private RewriteConfig parent;
    
    private String contextPath;
    private Boolean debug;
    private Boolean dumpXML;
    
    /**
     * Create instance.
     */
    public RewriteConfig() {
        this.clazz = null;
    }

    /**
     * Create instance associated with a given class.
     * 
     * <p>The class instance is currently only used for informational purposes.</p>
     * 
     * @param clazz associated class, never <code>null</code>
     */
    public RewriteConfig(Class<?> clazz) {
        Validate.notNull(clazz, "class must not be NULL");
        this.clazz = clazz;
    }
    
    /**
     * Sets the parent of this configuration.
     * 
     * <p>If a parent configuration is set, any information missing in the child instance
     * will be looked-up from the parent.</p>
     * 
     * @param parent parent configuration , never <code>null</code>
     */
    public void setParent(RewriteConfig parent) 
    {
        Validate.notNull(parent, "parent must not be NULL");
        this.parent = parent;
    }
    
    /**
     * Returns whether this configuration or any of its parents has at least one rewriting rule.
     * 
     * @return
     */
    public boolean hasRules() 
    {
        return ! hasNoRules();
    }
    
    /**
     * Returns whether this configuration nor any of its parents has any rewriting rules.
     * 
     * @return
     */    
    public boolean hasNoRules() 
    {
        if ( anonRules.isEmpty() && namedRules.isEmpty() ) {
            return parent == null ? true : parent.hasNoRules();
        }
        return false; 
    }    
    
    /**
     * Add rewriting rule.
     * 
     * @param rule
     * @throws IllegalStateException if this rule has an ID that clashes with another rule that has already been added to this instance. Anonymous rules (rules without an ID) can
     * never clash.
     */
    public void addRule(Rule rule) throws IllegalStateException {
        
        Validate.notNull(rule, "rule must not be NULL");
        if ( rule.isIDNotSet() ) {
          anonRules.add( rule );
          return;
          
        } 
        if ( namedRules.containsKey( rule.id ) )
        {
            final String msg = "Rule with duplicate ID '"+rule.id+"'"+( clazz != null ? " on class "+clazz : "" );
            throw new IllegalStateException( msg );
        }
        this.namedRules.put( rule.id , rule );
    }
    
    /**
     * Add rewriting rules.
     * 
     * @param rules
     * @throws IllegalStateException if any of the rules has an ID that clashes with another rule that has already been added to this instance. Anonymous rules (rules without an ID) can
     * never clash.
     */    
    public void addRules(Collection<Rule> rules) throws IllegalStateException {
        
        Validate.notNull(rules, "rule must not be NULL");
        rules.forEach( this::addRule );
    }
    
    /**
     * Returns all rewriting rules of this instance merged with any rules from parent instances.
     * 
     * <p>
     * Merging is done by applying the following algorithm:
     * 
     * 1.) Gather all anonymous rules (=rules without an explicit ID) 
     * 2.) Gather all named rules (=rules with an ID set) from <b>this</b> instance
     * 3.) Recursively gather named rules from parent configurations (if any) as long as they do no clash with the ID of a named rule
     *     that has already been merged
     * </p>
     *  
     * @return
     */
    public List<Rule> getRules() 
    {
        final List<Rule> result = new ArrayList<>(Math.max( 1 , anonRules.size() + namedRules.size() ) );
        
        result.addAll( anonRules );
        result.addAll( namedRules.values() );
        
        final Set<String> ruleIDs = new HashSet<>( namedRules.keySet() );
        
        if ( parent != null ) 
        {
            final List<Rule> parentRules = parent.getRules();
            parentRules.stream().filter( Rule::isIDNotSet ).forEach( result::add ); 
            parentRules.stream().filter( Rule::isIDSet ).filter( rule -> ! ruleIDs.contains( rule.id ) ).peek( r -> ruleIDs.add( r.id ) ).forEach( result::add ); 
        }
        return result;
    }
    
    /**
     * Returns the abstract path to the XML file that should be rewritten.
     *  
     * @return
     * @throws IllegalStateException if neither this configuration nor any of its parents has a non-blank context path.
     */
    public String getContextPath() throws IllegalStateException 
    {
        if ( contextPath != null ) {
            return contextPath;
        }
        if ( parent != null ) {
            return parent.getContextPath(); 
        }
        throw new IllegalStateException("Context path not set?");
    }
    
    /**
     * Returns a Spring <code>Resource</code> that can be used to retrieve the XML that should be rewritten.
     *  
     * @return
     * @throws IllegalStateException if neither this configuration nor any of its parents has a non-blank context path.
     */    
    public Resource getResource() throws  IllegalStateException
    {
        final String path = getContextPath();
        if ( path.startsWith("file:" ) ) {
            return new FileSystemResource( path.substring("file:".length() ) );
        }
        return new ClassPathResource( path );
    }
    
    /**
     * Sets the abstract path to the XML that should be rewritten.
     * 
     * @param contextPath context path, never <code>null</code> or blank
     * @throws IllegalArgumentException if the context path was blank
     * @throws NullPointerException if the context path was <code>null</code>
     */
    public void setContextPath(String contextPath) 
    {
        Validate.notBlank(contextPath, "contextPath must not be NULL or blank");
        this.contextPath = contextPath;
    }
    
    /**
     * Enable/disable debug output to std out.
     * 
     * <p>This is most useful for debugging this project.</p>
     * 
     * @param debug
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    /**
     * Returns whether debug output should be printed to std out.
     * 
     * @return
     */
    public boolean isDebug() 
    {
        if ( debug != null ) {
            return debug.booleanValue();
        }
        return parent != null ? parent.isDebug() : false;
    }
    
    /**
     * Set whether the transformed XML should be printed to std out.
     * @param dumpXML
     */
    public void setDumpXML(boolean dumpXML) {
        this.dumpXML = dumpXML;
    }
    
    /**
     * Returns whether the transformed XML should be printed to std out. 
     * @return
     */
    public boolean isDumpXML() {
        if ( dumpXML != null ) {
            return dumpXML.booleanValue();
        }
        return parent != null ? parent.isDumpXML() : false;
    }
}