## What's this ? 

When writing integration tests for XML-based Spring applications , one often has to duplicate quite a lot of Spring XML to properly setup a test ApplicationContext. 

This tiny project implements a custom TestContextBootstrapper that allows you to rewrite your 'real' ApplicationContext XML on-the-fly so that it can be used in integration tests.

## License

This code is released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0)

## How it works 

This project provides a custom TestContextBootstrapper along with some new annotations that allow you to transform your ApplicationContext XML using XPath expressions. The custom bootstrapper hooks into Spring's context loading mechanism and re-writes the ApplicationContext XML before it gets passed to Spring.

## Requirements (Building)

- Maven 3.x
- Spring 4.2.x (may work with earlier versions, not tested)
- JDK >= 1.8 

## Requirements (Running)

- Spring 4.2.x (may work with earlier versions, not tested)
- JDK >= 1.8 

## Usage

Instead of just annotating your integration test class with @ContextConfiguration , you now need to use @BootstrapWith to tell Spring which bootstrapper to use and use  
@ContextRewritingBootStrapper.ContextConfiguration instead of Spring's regular @ContextConfiguration so that Spring's context loading can be intercepted. 

To rewrite the ApplicationContext XML, you may use any number of the following annotations (note that only JDK >=1.8 allows repeating annotations):

| Annotation             | Attributesi                                    | Remarks                                                                                                              |
|--------- ------------- | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------.------------- |
| @ContextConfiguration  | value , debug , dumpRewrittenXML | 'value' holds the spring context path. 'debug' is optional and turns on debug output to stdout. 'dumpRewrittenXML' does just that. | 
| @ReplaceRule           | id, xpath , replacement , replacementClassName | 'id' attribute is optional. You may use either *replacement* **or** *replacementClassName* but not both.             |
| @RemoveRule            | id , xpath                                     | 'id' attribute is optional.                                                                                          |
| @InsertElementRule     | id , xpath , insert                            | 'id' attribute is optional.                                                                                          |
| @InsertAttributeRule   | id, xpath , name , value                       | 'id' attribute is optional.                                                                                          |

Annotations are parsed from all classes within a hierarchy so it's possible to have an abstract base class that performs some general transformations and then have more specific rules for individual tests.

The 'id' attribute on rewrite annotations is optional and used to override a rule with the same ID that was inherited from a parent class. IDs need to be unique for all rewrite annotations on a class.

### Basic usage 

```java
@BootstrapWith(value=ContextRewritingBootStrapper.class)
@ContextRewritingBootStrapper.ContextConfiguration(value="/spring-auth-test.xml",dumpRewrittenXML=true)
@ReplaceRule( xpath="/beans/bean[@id='settingsResource']/constructor-arg/@value" , replacement ="/some.properties" )
@RemoveRule( xpath="/beans/bean[@id='dataSource']" )
@RemoveRule( xpath="/beans/bean[@id='config']" )
@InsertRule( xpath="/beans/bean[@id='settingsResource']" , insert ="<constructor-arg value=\"other.properties\"/>" )
public class SpringIntegrationTest extends AbstractTransactionalJUnit4SpringContextTests
{
    @Test
    public void testSomething() {
        // ... do stuff ...
    }
}
```

### Advanced usage (inheritance and rule overriding)

```java
@BootstrapWith(value=ContextRewritingBootStrapper.class)
@ContextRewritingBootStrapper.ContextConfiguration(value="/spring-auth-test.xml",dumpRewrittenXML=true)
@ReplaceRule( id="configRule" , xpath="/beans/bean[@id='settingsResource']/constructor-arg/@value" , replacement ="/some.properties" )
public abstract class AbstractSpringIntegrationTest extends AbstractTransactionalJUnit4SpringContextTests
{
}

@ReplaceRule( id="configRule" , xpath="/beans/bean[@id='settingsResource']/constructor-arg/@value" , replacement ="/other.properties" )
@RemoveRule( xpath="/beans/bean[@id='config']" )
@InsertRule( xpath="/beans/bean[@id='settingsResource']" , insert ="<constructor-arg value=\"other.properties\"/>" )
public abstract class SpringIntegrationTest extends AbstractSpringIntegrationTest {
{   
}

```
