## What's this ? 

When writing integration tests for XML-based Spring applications , one often has to repeat quite a lot of XML to properly setup a test ApplicationContext. 

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

Instead of just annotating your integration test class with just @ContextConfiguration , you now also need to use @BootstrapWith to tell Spring which bootstrapper to use. 
You may want to replace @ContextConfiguration with @ContextRewritingBootStrapper.ContextConfiguration so that you can enable dumping the rewritten ApplicationContext to stdout (useful for debugging).

To rewrite the ApplicationContext XML, you may use any number of the following annotations (note that only JDK >=1.8 allows repeating annotations):

| Annotation    | Attributesi                                | Remarks     |
| ------------- | ------------------------------------------ | ----------- |
| @ReplaceRule  | xpath , replacement , replacementClassName | You may use either *replacement* **or** *replacementClassName* but not both. |
| @RemoveRule   | xpath                                      |             |
| @InsertRule   | xpath , insert                             |             |

### Usage Example

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
