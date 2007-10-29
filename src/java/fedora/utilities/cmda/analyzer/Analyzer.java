package fedora.utilities.cmda.analyzer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import fedora.server.storage.translation.DOSerializer;
import fedora.server.storage.translation.DOTranslationUtility;
import fedora.server.storage.types.DigitalObject;

import fedora.utilities.config.ConfigUtil;

import static fedora.utilities.cmda.analyzer.Constants.CHAR_ENCODING;

/**
 * Utility for analyzing a set of Fedora objects and outputting content
 * model objects and membership lists.
 * 
 * @author Chris Wilper
 */
public class Analyzer {
   
    //---
    // Property names
    //---
    
    /**
     * The property indicating which classifier to use;
     * <code>classifier</code>
     */
    public static final String CLASSIFIER_PROPERTY = "classifier";

    /**
     * The property indicating which object source to use;
     * <code>objectSource</code>
     */
    public static final String OBJECT_SOURCE_PROPERTY = "objectSource";
    
    /**
     * The property indicating which output directory to use:
     * <code>outputDir</code>
     */
    public static final String OUTPUT_DIR_PROPERTY = "outputDir";

    /**
     * The property indicating which serializer to use;
     * <code>serializer</code>
     */
    public static final String SERIALIZER_PROPERTY = "serializer";

    //---
    // Property defaults
    //---
    
    /**
     * The classifier that will be used if none is specified;
     * <code>fedora.utilities.cmda.analyzer.DefaultClassifier</code>
     */
    public static final String DEFAULT_CLASSIFIER
            = "fedora.utilities.cmda.analyzer.DefaultClassifier";

    /**
     * The object source that will be used if none is specified;
     * <code>fedora.utilities.cmda.analyzer.DirObjectSource</code>
     */
    public static final String DEFAULT_OBJECT_SOURCE
            = "fedora.utilities.cmda.analyzer.DirObjectSource";

    /**
     * The serializer that will be used if none is specified;
     * <code>fedora.server.storage.translation.FOXML1_1DOSerializer</code>
     */
    public static final String DEFAULT_SERIALIZER
            = "fedora.server.storage.translation.FOXML1_1DOSerializer";
    
    //---
    // Private constants
    //---

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(Analyzer.class);
   
    /** Prefix for generated content model object filenames. */
    private static final String CMODEL_PREFIX = "cmodel-";
    
    /** Suffix for generated content model object filenames. */
    private static final String CMODEL_SUFFIX = ".xml";

    /** Prefix for content model membership list filenames. */
    private static final String MEMBER_PREFIX = "cmodel-";
    
    /** Suffix for content model membership list filenames. */
    private static final String MEMBER_SUFFIX = ".members.txt";

    //---
    // Instance variables
    //---

    /** The classifier this instance uses. */
    private Classifier m_classifier;

    /** The output format of the content model objects. */
    private DOSerializer m_serializer;

    /** The directory the content model objects and lists will be sent to. */
    private File m_outputDir;

    /** The current number of distinct content models seen. */
    private int m_cModelCount;

    /** Map of content model to the order in which it was seen. */
    private Map<DigitalObject, Integer> m_cModelNumber;

    /** Map of content model to the PrintWriter for the list of members. */
    private Map<DigitalObject, PrintWriter> m_memberLists;

    //---
    // Constructors
    //---

    /**
     * Constructs an analyzer.
     *
     * @param classifier the classifier to use.
     * @param serializer the serializer to use for the output content models.
     */
    public Analyzer(Classifier classifier, DOSerializer serializer) {
        m_classifier = classifier;
        m_serializer = serializer;
    }

    /**
     * Constructs an analyzer with configuration taken from the given
     * properties.
     *
     * <p><b>Specifying the Classifier</b><br/>
     * If <code>classifier</code> is specified, an instance of the class it
     * names will be constructed by passing in the given properties to its
     * Properties (or no-arg) constructor.  Otherwise, the default classifier
     * will be used.
     *
     * <p><b>Specifying the Serializer</b><br/>
     * If <code>serializer</code> is specified, an instance of the class it
     * names will be constructed by passing in the given properties to its
     * Properties (or no-arg) constructor.  Otherwise, the default serializer
     * will be used.
     *
     * @param props the properties to get configuration from.
     */
    public Analyzer(Properties props) {
        m_classifier = (Classifier) ConfigUtil.construct(props,
                CLASSIFIER_PROPERTY, DEFAULT_CLASSIFIER);
        m_serializer = (DOSerializer) ConfigUtil.construct(props,
                SERIALIZER_PROPERTY, DEFAULT_SERIALIZER);
    }
    
    //---
    // Public interface
    //---

    /**
     * Iterates the given objects, classifying them and sending output
     * to the given directory.
     *
     * @param objects iterator of objects to classify.
     * @param outputDir the directory to send output to.  It must not contain
     *                  any files.  If it doesn't yet exist, it will be
     *                  created.
     */
    public void classifyAll(ObjectSource objects, File outputDir) {
        clearState();
        setOutputDir(outputDir);
        try {
            while (objects.hasNext()) {
                DigitalObject object = objects.next();
                DigitalObject cModel = m_classifier.getContentModel(object);
                recordMembership(object, cModel);
            }
            serializeCModels();
        } finally {
            closeMemberLists();
        }
    }
    
    //---
    // Instance helpers
    //---

    private void serializeCModels() {
        for (DigitalObject object : m_cModelNumber.keySet()) {
            int num = m_cModelNumber.get(object).intValue();
            File file = new File(m_outputDir, CMODEL_PREFIX + num
                    + CMODEL_SUFFIX);
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file);
                m_serializer.getInstance().serialize(
                        object, out, CHAR_ENCODING, 
                        DOTranslationUtility.SERIALIZE_EXPORT_MIGRATE);
            } catch (Exception e) {
                throw new RuntimeException(Messages.ERR_SERIALIZE_FAILED, e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception e) {
                        LOG.error(Messages.ERR_CLOSE_FILE_FAILED
                                + file.getPath());
                    }
                }
            }
        }
    }

    private void recordMembership(DigitalObject object,
            DigitalObject cModel) {
        PrintWriter writer = m_memberLists.get(cModel);
        if (writer == null) {
            m_cModelCount++;
            m_cModelNumber.put(cModel, new Integer(m_cModelCount));
            try {
                writer = new PrintWriter(new OutputStreamWriter(
                        new FileOutputStream(
                                new File(m_outputDir, MEMBER_PREFIX
                                        + m_cModelCount + MEMBER_SUFFIX))));
                m_memberLists.put(cModel, writer);
            } catch (IOException e) {
                throw new RuntimeException(Messages.ERR_WRITE_FILE_FAILED, e);
            }
        }
        writer.println(object.getPid());
    }

    private void closeMemberLists() {
        for (PrintWriter writer : m_memberLists.values()) {
            writer.close();
        }
        m_memberLists.clear();
    }

    private void setOutputDir(File outputDir) {
        if (!outputDir.exists()) {
            outputDir.mkdir();
            if (!outputDir.exists()) {
                throw new RuntimeException(Messages.ERR_MKDIR_FAILED
                        + outputDir.getPath());
            }
        }
        if (outputDir.listFiles().length != 0) {
            throw new RuntimeException(Messages.ERR_DIR_NONEMPTY
                    + outputDir.getPath());
        }
        m_outputDir = outputDir;
    }

    private void clearState() {
        m_memberLists = new HashMap<DigitalObject, PrintWriter>();
        m_cModelNumber = new HashMap<DigitalObject, Integer>();
        m_cModelCount = 0;
    }
    
    //---
    // Command-line
    //---
    
    /**
     * Command-line entry point for the analyzer.
     * 
     * @param args command-line arguments.
     */
    public static void main(String[] args) {
        // HACK: make DOTranslatorUtility happy
        System.setProperty("fedoraServerHost", "localhost");
        System.setProperty("fedoraServerPort", "80");
        // HACK: make commons-logging happy
        final String pfx = "org.apache.commons.logging.";
        if (System.getProperty(pfx + "LogFactory") == null) {
            System.setProperty(pfx + "LogFactory", pfx + "impl.Log4jFactory");
            System.setProperty(pfx + "Log", pfx + "impl.Log4JLogger");
        }
        if (args.length != 1) {
            System.out.println(Messages.ANALYZER_USAGE);
            System.exit(0);
        } else {
            if (args[0].equals("--help")) {
                System.out.println(Messages.ANALYZER_HELP);
                System.exit(0);
            }
            try {
                Properties props;
                if (args[0].equals("--")) {
                    props = System.getProperties();
                } else {
                    props = new Properties();
                    props.load(new FileInputStream(args[0]));
                }
                Analyzer analyzer = new Analyzer(props);
                ObjectSource source = (ObjectSource) ConfigUtil.construct(props,
                        OBJECT_SOURCE_PROPERTY, DEFAULT_OBJECT_SOURCE);
                String outputDir = ConfigUtil.getRequiredString(props,
                        OUTPUT_DIR_PROPERTY);
                analyzer.classifyAll(source, new File(outputDir));
            } catch (FileNotFoundException e) {
                LOG.error(Messages.ERR_CONFIG_NOT_FOUND + args[0]);
                System.exit(1);
            } catch (IllegalArgumentException e) {
                LOG.error(e.getMessage());
                System.exit(1);
            } catch (Throwable th) {
                LOG.error(Messages.ERR_ANALYSIS_FAILED, th);
                System.exit(1);
            }
        }
    }
}
