package fedora.utilities.cmda.analyzer;

import fedora.server.storage.types.DigitalObject;

/**
 * Interface for classifying Fedora objects based on their content.
 *
 * @author Chris Wilper
 */
public interface Classifier {

    /**
     * Gets a content model object appropriate to the given object.
     * 
     * @param obj the object to examine.
     * @return a content model object that describe the class of object
     *         it belongs to.
     */
    DigitalObject getContentModel(DigitalObject obj);
   
    /**
     * Gets Behavior Mechanism Directives for the given content model,
     * which was previously returned by this classifier.
     * 
     * <p>BMech directives specify an original BMech PID, a new BMech PID
     * (for the copy), and a set of necessary wsdl message part name changes.
     * This information is expected to be used by the generator in creating
     * new BMech objects.</p>
     * 
     * Example BMech Directives:
     * <pre>
     * OLD_BMECH demo:BMech1
     * NEW_DEPLOYMENTS demo:GeneratedBMech1
     * NEW_PARTS FULL_SIZE=DS1 MEDIUM_SIZE=DS2
     * OLD_BMECH foo
     * NEW_BMECH bar
     * NEW_PARTS baz
     * </pre>
     * 
     * @param cModelPID identifies the content model whose directives to get.
     * @return the directives, or null if no BMech directives exist.
     */
    String getBMechDirectives(String cModelPID);

}
