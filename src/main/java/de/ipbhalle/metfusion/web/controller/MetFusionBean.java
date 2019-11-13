/**
 * created by Michael Gerlich on May 21, 2010
 * last modified May 21, 2010 - 4:07:10 PM
 * email: mgerlich@ipb-halle.de
 */
package de.ipbhalle.metfusion.web.controller;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.event.ValueChangeEvent;
import javax.faces.model.SelectItem;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.WritableCell;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableImage;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

import org.icefaces.application.PortableRenderer;
import org.icefaces.application.PushRenderer;

import com.icesoft.faces.context.Resource;
import com.icesoft.faces.context.effects.Appear;
import com.icesoft.faces.context.effects.Effect;
import com.icesoft.faces.context.effects.Fade;
import com.icesoft.faces.context.effects.Highlight;

import de.ipbhalle.MassBank.MassBankLookupBean;
import de.ipbhalle.enumerations.AvailableParameters;
import de.ipbhalle.enumerations.ResultTabs;
import de.ipbhalle.enumerations.SpectralDB;
import de.ipbhalle.metfusion.threading.MetFusionThread;
import de.ipbhalle.metfusion.utilities.output.SDFOutputHandler;
import de.ipbhalle.metfusion.wrapper.ColorcodedMatrix;
import de.ipbhalle.metfusion.wrapper.Result;
import de.ipbhalle.metfusion.wrapper.ResultExt;
import de.ipbhalle.metfusion.wrapper.SDFResource;
import de.ipbhalle.metfusion.wrapper.XLSOutputHandler;
import de.ipbhalle.metfusion.wrapper.XLSResource;


//@ManagedBean(name = "appBean", eager = true)
//@ApplicationScoped
@ManagedBean(name="appBean")
//@CustomScoped(value = "#{window}")
@SessionScoped
public class MetFusionBean implements Serializable {
	
	private Highlight effectOutputText = new Highlight("#FFA500");
	private Highlight effectNote = new Highlight("#FFA500");
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * bean for MassBank lookup and result retrieval
	 */
	private MassBankLookupBean mblb;
	
	/** bean for Metlin lookup and result retrieval */
	private MetlinBean mb;
	
	/** bean for GMD lookup and result retrieval */
	private GMDBean gmdb;
	
	/** generic database bean that resembles an interface all database beans have to implement */
	private GenericDatabaseBean genericDatabase;
	
	/**
	 * bean for MetFrag query and result retrieval
	 */
	private MetFragBean mfb;
	
	/**
	 * result list of threshold approach
	 */
	private List<ResultExt> newOrder;
	
	/**
	 * result list of weighted approach
	 */
	private List<ResultExt> secondOrder;
	
	
	private List<ResultExtGroupBean> tanimotoClusters;
	
	/**
	 * color coded matrix of Tanimoto values for MassBank vs. MetFrag
	 */
	private ColorcodedMatrix colorMatrix;
	
	private ColorcodedMatrix colorMatrixAfter;
	/**
	 * boolean value to indicate whether to show or hide result tables
	 */
	private boolean showTable;
	
	private boolean showResultTable;
	private boolean showResultsDatabase;
	private boolean showResultsFragmenter;
	private boolean showClusterResults;
	
	private String selectedTab = "0";
	private String selectedMatrixPanel = "origSimMatrix";
	
	/** default to MassBank spectral db*/
	private String selectedSpectralDB = SpectralDB.MassBank.getPanel();
	
	/** setup this list of available spectral dbs at startup */
	private SelectItem[] availableSpectralDBsSI;
	
	/**
	 * peaklist of input spectrum, containing pairwise mz and intensity values
	 */
	private String inputSpectrum = "119.051 46\n123.044 37\n147.044 607\n153.019 999\n179.036 14\n189.058 17\n273.076 999\n274.083 31"; 
		//"273.096 22\n289.086 107\n290.118 14\n291.096 999\n292.113 162\n293.054 34\n579.169 37\n580.179 15";
	
	/**
	 * boolean value to indicate whether to use clustering or not
	 */
	private boolean useClustering = true;
	
	/**
	 * boolean indicating whether candidates should be filtered according to their InChI-Key 1 (true)
	 * or not (false). This filter step would reduce the list of candidates by removing stereoisomers.
	 */
	private boolean useInChIFiltering = true;
	
	private String selectedResult = "cluster";	// allows switching of panels in panelStacking of ICEFaces
	// other values are "fragmenter", "database", "list"
	
	// chart variables
	private List<Color> chartColors;
	private List<double[]> chartValues;	// double[] for each pair value
	
	private final int numThreads = 4;

	private HttpSession session;
	private String sessionString;	// = session.getId();
	private ServletContext scontext;
	private ExternalContext ec;
	private final String sep = System.getProperty("file.separator");
	private String webRoot;
	private Locale locale;
	
	private String errorMessage = "";
	private String metfragModeSetting = "MetFrag is set to work in <b>positive</b> mode";	// "Currently, only positive mode is supported!"
	
	/** output resource for all workflow results, will be stored in xls file */
    private Resource outputResource;
    private XLSOutputHandler exporter;
    private boolean createdResource = false;
    
    /** output resource for reranked results, stored in SD file */
    private boolean createdResourceSDF = false;
    private Resource outputResourceSDF;
    
    /** output resource for database results, stored in SD file */
    private boolean createdResourceSDFDatabase = false;
    private Resource outputResourceSDFDatabase;
    
    /** output resource for fragmenter results, stored in SD file */
    private boolean createdResourceSDFFragmenter = false;
    private Resource outputResourceSDFFragmenter;
    
    /** output resource for unused results, stored in SD file */
    private boolean createdResourceSDFUnused = false;
    private Resource outputResourceSDFUnused;
    
    
    /** progress bar rendering */
    private static final int PAUSE_AMOUNT_S = 1000; // milliseconds to pause between progress updates
    private Thread updateThreadDatabase;	// updater thread for database lookup
    private Thread updateThreadFragmenter;	// updater thread for fragmenter computation
    private Thread updateThreadGlobal;		// global update thread for MetFusion
    private boolean isRunning = false;
    private PortableRenderer renderer;
    private int percentProgress = 0;
    private int percentProgressGlobal = 0;
    private int percentProgressDatabase = 0;
    private int percentProgressFragmenter = 0;
    private static final String PUSH_GROUP = "all";
    private MetFusionThread mfthread;
    private String status;
    private boolean enableStart = Boolean.TRUE;
    private Effect effect;
    
    /** thread handling for parallel processing */
    private int threads = 1;
	private ExecutorService threadExecutor = null;
    private int overallProgress = 0;	// progress for all intermediate steps
    
    private String navigate = "";
    
    private PropertiesBean props;
    
    public MetFusionBean() {
//		setMblb(new MassBankLookupBean());
//		setMfb(new MetFragBean());
		
		chartColors = new ArrayList<Color>();
		chartColors.add(this.decode("#FF0000"));
		chartColors.add(this.decode("#00FF00"));
		chartColors.add(this.decode("#0000FF"));
		
		chartValues = new ArrayList<double[]>();
		
		FacesContext fc = FacesContext.getCurrentInstance();
//		ELResolver el = fc.getApplication().getELResolver();
//        ELContext elc = fc.getELContext();
		this.ec = fc.getExternalContext();
		session = (HttpSession) ec.getSession(false);
		this.sessionString = session.getId();
		System.out.println("MetFusionBean sessionID -> " + sessionString);
		this.locale = fc.getViewRoot().getLocale();
		//this.mfb.setSessionID(sessionString);
//		MassBankLookupBean mblb = (MassBankLookupBean) el.getValue(elc, null, "massBankLookupBean");
//		MetFragBean mfb = (MetFragBean) el.getValue(elc, null, "metFragBean");
//		setMblb(mblb);
//		setMfb(mfb);
		
		PushRenderer.addCurrentSession(PUSH_GROUP);
   		renderer = PushRenderer.getPortableRenderer();
   		
		// retrieve current session bean
		ELContext elc = fc.getELContext();
		ELResolver el = fc.getApplication().getELResolver();
		/** retrieve session's database and fragmenter beans */
		this.mblb = (MassBankLookupBean) el.getValue(elc, null, "databaseBean");
		this.mfb = (MetFragBean) el.getValue(elc, null, "fragmenterBean");
		this.mfb.setSessionID(sessionString);
		this.mb = (MetlinBean) el.getValue(elc, null, "metlinBean");
		this.gmdb = (GMDBean) el.getValue(elc, null, "gmdBean");
		
		// set number of threads accordingly
		Runtime runtime = Runtime.getRuntime();
		this.threads = runtime.availableProcessors();
		this.threadExecutor = Executors.newFixedThreadPool(threads);
		System.out.println("threads -> " + threads);
		
		// setup properties bean
		this.props = (PropertiesBean) ec.getApplicationMap().get("propertiesBean");
		
		// setup selector for spectral db
		setupAvailableSpectralDBs();
	}
    
    private void setupAvailableSpectralDBs() {
    	SpectralDB[] values = SpectralDB.values();
    	availableSpectralDBsSI = new SelectItem[values.length];
    	for (int i = 0; i < values.length; i++) {
    		availableSpectralDBsSI[i] = new SelectItem(values[i].getPanel(), values[i].getLabel());
		}
    }
    
    public void toggleEffect() {
    	if(!enableStart) {							// let progress bars appear
    		effect = new Appear();
    	}
    	
    	if(genericDatabase.isDone() && mfb.isDone()){			// let progress bars vanish
    		effect = new Fade();
    	}
    	//effect.setFired(false);
    }
    
    private void resetBeans() {
    	this.percentProgress = 0;
    	this.percentProgressDatabase = 0;
    	this.percentProgressFragmenter = 0;
    	this.percentProgressGlobal = 0;
    	this.setCreatedResource(false);
    	this.setCreatedResourceSDF(Boolean.FALSE);
    	this.setCreatedResourceSDFDatabase(Boolean.FALSE);
    	this.setCreatedResourceSDFFragmenter(Boolean.FALSE);
    	this.setCreatedResourceSDFUnused(Boolean.FALSE);
    	
    	setShowClusterResults(Boolean.FALSE);
    	mblb.setSearchProgress(0);
    	mfb.setProgress(0);
    	mb.setSearchProgress(0);
    	
    	mblb.setDone(Boolean.FALSE);
    	mfb.setDone(Boolean.FALSE);
    	mb.setDone(Boolean.FALSE);
    	
    	this.tanimotoClusters = new ArrayList<ResultExtGroupBean>();
    	this.secondOrder = new ArrayList<ResultExt>();
    	mfb.setResults(new ArrayList<Result>());
    	mblb.setResults(new ArrayList<Result>());
    	mb.setResults(new ArrayList<Result>());
    }
    
    public String runThreadedVersion() {
    	FacesContext fc = FacesContext.getCurrentInstance();
    	
    	setEnableStart(Boolean.FALSE);
    	
    	resetBeans();
    	
    	// set context environment
		session = (HttpSession) fc.getExternalContext().getSession(false);
		scontext = (ServletContext) fc.getExternalContext().getContext();
		String sep = "/";
		webRoot = scontext.getRealPath(sep);
		String sessionPath = webRoot + sep + "temp" + sep + sessionString + sep;
		System.out.println("tempPath -> " + sessionPath);
		System.out.println("sessionID -> " + sessionString);
		String tempDir = sep + "temp" + sep + sessionString + sep;
		
		// MetFrag mode of operation
		int mode = 0;		// initialize with both modes
		
    	if(selectedSpectralDB.equals(SpectralDB.MassBank.getPanel())) {
    		mblb.collectInstruments();
        	mblb.setInputSpectrum(inputSpectrum);
        	// set usage of InChI-based filtering
        	mblb.setUniqueInchi(useInChIFiltering);
        	mblb.setSessionPath(sessionPath);
        	
        	String[] insts = mblb.getSelectedInstruments();
            // check if instruments were selected
            if(insts == null || insts.length == 0) {
            	String errMessage = "Error - no instruments were selected!";
                System.err.println(errMessage);
                FacesMessage currentMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, errMessage, errMessage);
                fc.addMessage("inputForm:errMsgInst", currentMessage);
                
                setShowResultsDatabase(false);
                setShowTable(true);
                //this.navigate = "errorInstrument";
            }
            
            try {
    			mode = Integer.parseInt(mblb.getSelectedIon());
    		}
    		catch(NumberFormatException e) {
    			mode = 1;		// default to positive mode
    		}
        	
        	genericDatabase = mblb;
    	}
    	else if(selectedSpectralDB.equals(SpectralDB.GMD.getPanel())) {
    		gmdb.setInputSpectrum(inputSpectrum);
    		gmdb.setSessionPath(sessionPath);
    		// set usage of InChI-based filtering
        	gmdb.setUniqueInchi(useInChIFiltering);
        	
        	genericDatabase = gmdb;
    	}
    	else if(selectedSpectralDB.equals(SpectralDB.Metlin.getPanel())) {
    		mb.setInputSpectrum(inputSpectrum);
        	mb.setPrecursorMass((float) mfb.getExactMass());
        	mb.setSessionPath(sessionPath);
        	// set usage of InChI-based filtering
        	mb.setUniqueInchi(useInChIFiltering);

        	if(mb.getSelectedIonization().equals("pos"))
        		mode = 1;
        	else if (mb.getSelectedIonization().equals("neg"))
        		mode = -1;
        	else mode = 1;
        	
        	genericDatabase = mb;
    	}
    	else {
    		System.err.println("Unknown selected spectral database! - Aborting.");
    		
    		return "unknownDatabase";
    	}
    	
    	// set fragmenter parameters
    	if(mode == 0) // MassBank uses "both" ionizations, but MetFrag would be set GC-MS
			mfb.setMode(1);		// switch to "positive" mode
    	else mfb.setMode(mode);
    	
    	mfb.setInputSpectrum(inputSpectrum);
    	// set usage of InChI-based filtering
    	mfb.setUniqueInchi(useInChIFiltering);
    	mfb.setSessionPath(sessionPath);
    	
        
        System.out.println("runBoth started!!!");
        ELResolver el = fc.getApplication().getELResolver();
        ELContext elc = fc.getELContext();
        StyleBean styleBean = (StyleBean) el.getValue(elc, null, "styleBean");
        
    	mfthread = new MetFusionThread(this, genericDatabase, mfb, styleBean, tempDir);
    	
    	// Create the database progress thread
    	updateThreadDatabase = new Thread(new Runnable() {
            public void run() {
            	while(percentProgress <= 100) {
	            	try {
	    				Thread.sleep(PAUSE_AMOUNT_S);
	    			} catch (InterruptedException e) {
	    				System.err.println("Error while putting updateThread to sleep!");
	    				e.printStackTrace();
	    			}
	    			
	    			percentProgress = genericDatabase.getSearchProgress();
	    			percentProgressDatabase = percentProgress;
	    			
	    			// send updated progress to outputProgress component
	    			renderer.render(PUSH_GROUP);
	    			
	    			// break loop if reached 100%
	    			if(percentProgress == 100 || genericDatabase.isDone()) {
	    				percentProgress = 100;
	    				percentProgressGlobal += 10;	// when finished, increase global progress completed percentage
	    				// send updated progress to outputProgress component
		    			renderer.render(PUSH_GROUP);
	    				break;
	    			}
	            }
            }
        }, "updateThreadDatabase");
        
        // create the fragmenter updater thread
        updateThreadFragmenter = new Thread(new Runnable() {
            public void run() {
            	while(percentProgressFragmenter <= 100) {
	            	try {
	    				Thread.sleep(PAUSE_AMOUNT_S);
	    			} catch (InterruptedException e) {
	    				System.err.println("Error while putting updateThreadFragmenter to sleep!");
	    				e.printStackTrace();
	    			}
	    			
	    			percentProgressFragmenter = mfb.getProgress();
	    			
	    			// send updated progress to outputProgress component
	    			renderer.render(PUSH_GROUP);
	    			
	    			// break loop if reached 100%
	    			if(percentProgressFragmenter == 100 || mfb.isDone()) {
	    				percentProgress = 100;
	    				percentProgressGlobal += 10;	// when finished, increase global progress completed percentage
	    				// send updated progress to outputProgress component
		    			renderer.render(PUSH_GROUP);
	    				break;
	    			}
	            }
            }
        }, "updateThreadFragmenter");
        
        // create global updater thread
        updateThreadGlobal = new Thread(new Runnable() {
            public void run() {
            	percentProgressGlobal = 1;	// initialize indeterminate progress bar
            	while(percentProgressGlobal <= 100) {
	            	try {
	    				Thread.sleep(PAUSE_AMOUNT_S);
	    			} catch (InterruptedException e) {
	    				System.err.println("Error while putting updateThreadGlobal to sleep!");
	    				e.printStackTrace();
	    			}
	    			
	    			if(mfthread.getProgress() > percentProgressGlobal)	// use pseudo progress from finished retrieval threads
	    				percentProgressGlobal = mfthread.getProgress();	// both threads increase per 10 = 20% -> first getProgress starts at 25%
	    			
	    			// send updated progress to outputProgress component
	    			renderer.render(PUSH_GROUP);
	    			
	    			// break loop if reached 100%
	    			if(percentProgressGlobal == 100 || isShowClusterResults())	{	// break if overall progress is 100% 
	    				setPercentProgressGlobal(100);
	    				setEnableStart(Boolean.TRUE);								// or clustering is done (final step)
	    				break;
	    			}
	            }
            }
        }, "updateThreadGlobal");
        
        threadExecutor = Executors.newFixedThreadPool(numThreads);
        threadExecutor.execute(mfthread);
        threadExecutor.execute(updateThreadDatabase);
        threadExecutor.execute(updateThreadFragmenter);
        threadExecutor.execute(updateThreadGlobal);
        threadExecutor.shutdown();
		
        System.out.println("threaded end!");
    	return "threaded";
    }
    
    public String stopExecution() {
    	
    	//updateThreadGlobal.
    	return "stopped";
    }
	
    /**
     * Generates an output resource for the current workflow results, the MetFusion results including properties are
     * stored in an SD file.
     */
    public void generateOutputResourceSDFHandler() {
    	// setup filename
    	SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy_k-m-s");
		String time = sdf.format(new Date());
		String resourceName = "MetFusion_Results_" + time +  ".sdf";
		String folder = "./temp" + sep + sessionString + sep;
		
		String path = webRoot + sep + "temp" + sep + sessionString + sep;
		System.out.println("resource path -> " + path);
		
		File dir = new File(path);
		if(!dir.exists())
			dir.mkdirs();
		// skip creation of output resource if file access is denied
		if(!dir.canWrite()) {
			setCreatedResource(Boolean.FALSE);
			return;
		}
		
		String completeName = path + resourceName;
    	
		SDFOutputHandler sdfHandler = new SDFOutputHandler(completeName);
		sdfHandler.writeRerankedResults(getSecondOrder());
		
		// create SDFResource
    	SDFResource resource = new SDFResource(ec, resourceName, folder);
    	// set SDFResource
		setOutputResourceSDF(resource);
		// enable download button for resource
		setCreatedResourceSDF(Boolean.TRUE);
    }
    
    public void generateOriginalResultResourceSDFHandler(List<Result> list, ResultTabs tab) {
    	// setup filename
    	SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy_k-m-s");
		String time = sdf.format(new Date());
		String resourceName = tab.toString() + "_" + time +  ".sdf";
		String folder = "./temp" + sep + sessionString + sep;
		
		String path = webRoot + sep + "temp" + sep + sessionString + sep;
		System.out.println("resource path -> " + path);
		
		File dir = new File(path);
		if(!dir.exists())
			dir.mkdirs();
		// skip creation of output resource if file access is denied
		if(!dir.canWrite()) {
			setCreatedResource(Boolean.FALSE);
			return;
		}
		
		String completeName = path + resourceName;
    	
		SDFOutputHandler sdfHandler = new SDFOutputHandler(completeName);
		sdfHandler.writeOriginalResults(list, false);	// default to uncompressed file
		
		// create SDFResource
    	SDFResource resource = new SDFResource(ec, resourceName, folder);
    	if(tab.equals(ResultTabs.database)) {
    		// set SDFResource
    		setOutputResourceSDFDatabase(resource);
    		// enable download button for resource
    		setCreatedResourceSDFDatabase(Boolean.TRUE);
    	}
    	else if (tab.equals(ResultTabs.fragmenter)) {
    		// set SDFResource
    		setOutputResourceSDFFragmenter(resource);
    		// enable download button for resource
    		setCreatedResourceSDFFragmenter(Boolean.TRUE);
    	}
    	else if (tab.equals(ResultTabs.unused)) {
    		// set SDFResource
    		setOutputResourceSDFUnused(resource);
    		// enable download button for resource
    		setCreatedResourceSDFUnused(Boolean.TRUE);
    	}
    	else {
    		
    	}
    }
    
    private Map<AvailableParameters, Object> fetchSettings() {
    	Map<AvailableParameters, Object> m = new HashMap<AvailableParameters, Object>();
    	m.put(AvailableParameters.clustering, isUseClustering());
    	m.put(AvailableParameters.mbCutoff, mblb.getCutoff());
    	m.put(AvailableParameters.mbInstruments, mblb.getSelectedInstruments());
    	m.put(AvailableParameters.mbIonization, mblb.getSelectedIon());
    	m.put(AvailableParameters.mbLimit, mblb.getLimit());
    	m.put(AvailableParameters.mfAdduct, mfb.getSelectedAdduct());
    	m.put(AvailableParameters.mfDatabase, mfb.getSelectedDB());
    	m.put(AvailableParameters.mfDatabaseIDs, mfb.getDatabaseID());
    	m.put(AvailableParameters.mfExactMass, mfb.getExactMass());
    	m.put(AvailableParameters.mfFormula, mfb.getMolecularFormula());
    	m.put(AvailableParameters.mfLimit, mfb.getLimit());
    	m.put(AvailableParameters.mfMZabs, mfb.getMzabs());
    	m.put(AvailableParameters.mfMZppm, mfb.getMzppm());
    	m.put(AvailableParameters.mfParentIon, mfb.getParentIon());
    	m.put(AvailableParameters.mfSearchPPM, mfb.getSearchppm());
    	m.put(AvailableParameters.onlyCHNOPS, mfb.isOnlyCHNOPS());
    	m.put(AvailableParameters.peaks, mblb.getInputSpectrum());
    	m.put(AvailableParameters.substrucAbsent, "not yet implemented");
    	m.put(AvailableParameters.substrucPresent, "not yet implemented");
    	m.put(AvailableParameters.spectralDB, genericDatabase.getDatabaseName());
    	
    	return m;
    }
    
    /**
     * Generates an output resource for the current workflow results, everything is stored inside a single Excel xls file
	 * where each workflow output ports is stored as a separate sheet, also including the computed colored matrices.
     */
    public void generateOutputResourceXLSHandler() {
    	// setup filename
    	SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy_k-m-s");
		String time = sdf.format(new Date());
		String resourceName = "MetFusion_Results_" + time +  ".xls";
		String folder = "./temp" + sep + sessionString + sep;
		
		String path = webRoot + sep + "temp" + sep + sessionString + sep;
		System.out.println("resource path -> " + path);
		
		File dir = new File(path);
		if(!dir.exists())
			dir.mkdirs();
		// skip creation of output resource if file access is denied
		if(!dir.canWrite()) {
			setCreatedResource(Boolean.FALSE);
			return;
		}
		
		String completeName = path + resourceName;
		
		// generate XLS file with specific output handler
		de.ipbhalle.metfusion.utilities.output.XLSOutputHandler xlsHandler = 
			new de.ipbhalle.metfusion.utilities.output.XLSOutputHandler(completeName, genericDatabase.getDatabaseName(), "MetFrag");
		
		// store results
        xlsHandler.writeAllResults(mfb.getResults(), genericDatabase.getResults(), getSecondOrder(), getTanimotoClusters());
        xlsHandler.writeOriginalMatrix(getColorMatrix(), "Original Matrix");
        xlsHandler.writeModifiedMatrix(getColorMatrixAfter(), "Reranked Matrix");
        xlsHandler.writeSettings(fetchSettings());
        
        try {
			xlsHandler.finishWorkbook(false);	// default to uncompressed file
			
			// create XLSResource
	    	XLSResource xls = new XLSResource(ec, resourceName, folder);
	    	// set XLSResource
			setOutputResource(xls);
			// enable download button for resource
			setCreatedResource(Boolean.TRUE);
		} catch (IOException e2) {
			System.err.println("Could not write xls file [" + completeName + "]");
			setCreatedResource(Boolean.FALSE);
		}
    }
    
	/** generates an output resource for the current workflow results, everything is stored inside a single Excel xls file
	 *  where each workflow output ports is stored as a separate sheet  */
	public void generateOutputResource() {
//		FacesContext fc = FacesContext.getCurrentInstance();
//		ExternalContext ec = fc.getExternalContext();
//		HttpSession session = (HttpSession) ec.getSession(false);
//		String sessionString = session.getId();
//		ServletContext sc = (ServletContext) ec.getContext();
//		String appPath = sc.getRealPath(".");
		
		//long time = new Date().getTime();
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy_k-m-s");
		String time = sdf.format(new Date());
//		String path = appPath + sep + "temp" + sep + sessionString + sep;
		String path = webRoot + sep + "temp" + sep + sessionString + sep;
		System.out.println("resource path -> " + path);
		
		File dir = new File(path);
		if(!dir.exists())
			dir.mkdirs();
		// skip creation of output resource if file access is denied
		if(!dir.canWrite())
			return;
		
		
		String resourceName = "MetFusion_Results_" + time +  ".xls";
		String folder = "./temp" + sep + sessionString + sep;
		File f = new File(dir, resourceName);
		System.out.println("outputresource -> " + f.getAbsolutePath());
		boolean createFile = false;
		try {
			createFile = f.createNewFile();
			if(!createFile) {
				System.err.println("Error creating new file for Excel output [" + f.getAbsolutePath() + "]!");
				return;
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		// create new Excel file
		WritableSheet sheet = null;
		WritableWorkbook workbook = null;
		WorkbookSettings settings = new WorkbookSettings();
		settings.setLocale(locale);
		try {
			workbook = Workbook.createWorkbook(f);
		} catch (IOException e) {
			e.printStackTrace();
			createdResource = false;
			return;
		}
//		String mimeType = "application/vnd.ms-excel";
		
		// write labels
		WritableFont arial12font = new WritableFont(WritableFont.ARIAL, 12);
		WritableCellFormat arial12format = new WritableCellFormat(arial12font);
		try {
			arial12font.setBoldStyle(WritableFont.BOLD);
		} catch (WriteException e) {
			e.printStackTrace();
			createdResource = false;
			return;
		}
		
		// font for text
		WritableFont arial10font = new WritableFont(WritableFont.ARIAL, 10);
		WritableCellFormat arial10format = new WritableCellFormat(arial10font);
		int currentSheet = 0;
		WritableImage wi = null;
		
		if(secondOrder.size() > 0) {
			// set sheet name (output port) and position
			sheet = workbook.createSheet("MetFusion Results", currentSheet);//, outputs.indexOf(port));
			currentSheet++;
			WritableCell headerRank = new Label(0, 0, "Rank", arial12format);
			WritableCell headerID = new Label(1, 0, "ID", arial12format);
			WritableCell headerName = new Label(2, 0, "Compound Name", arial12format);
			WritableCell headerOrigScore = new Label(3, 0, "Original Score", arial12format);
			WritableCell headerNewScore = new Label(4, 0, "MetFusion Score", arial12format);
			WritableCell headerStructure = new Label(5, 0, "Structure", arial12format);
			WritableCell headerSmiles = new Label(6, 0, "SMILES", arial12format);
			WritableCell headerInchi = new Label(7, 0, "InChI", arial12format);
			try
			{
				sheet.addCell(headerRank);
				sheet.addCell(headerID);
				sheet.addCell(headerName);
				sheet.addCell(headerOrigScore);
				sheet.addCell(headerNewScore);
				sheet.addCell(headerStructure);
				sheet.addCell(headerSmiles);
				sheet.addCell(headerInchi);
			} catch (WriteException e) {
				System.out.println("Could not write Excel sheet headers!");
				e.printStackTrace();
//				createdResource = false;
//				return;
			}
			
			int currentRow = 0;
			int currentCol = 0;
			// write MetFusion results
			for (ResultExt result : secondOrder) {
				//currentRow = counter*4 + 1;
				currentRow++;
				
				currentCol = 0;
				// output is text
				WritableCell cellRank = new Number(currentCol, currentRow, result.getTiedRank(), arial10format);
				currentCol++;
				WritableCell cellID = new Label(currentCol, currentRow, result.getId(), arial10format);
				currentCol++;
				WritableCell cellName = new Label(currentCol, currentRow, result.getName(), arial10format);
				currentCol++;
				WritableCell cellOrigScore = new Number(currentCol, currentRow, result.getScoreShort(), arial10format);
				currentCol++;
				WritableCell cellNewScore = new Number(currentCol, currentRow, result.getResultScore(), arial10format);
				currentCol++;
				//wi = new WritableImage(5, currentRow, 1, 3, new File(appPath, result.getImagePath()));
				File temp = new File(webRoot, result.getImagePath());
				if(temp.exists()) {
					wi = new WritableImage(currentCol, currentRow, 1, 3, temp);
					currentCol++;
				}
				//wi = new WritableImage(5, currentRow, 1, 3, new File(webRoot, result.getImagePath()));
				
				WritableCell cellSmiles = new Label(currentCol, currentRow, result.getSmiles(), arial10format);
				currentCol++;
				WritableCell cellInchi = new Label(currentCol, currentRow, result.getInchi(), arial10format);
				currentCol++;
				
				try
				{
					sheet.addCell(cellRank);
					sheet.addCell(cellID);
					sheet.addCell(cellName);
					sheet.addCell(cellOrigScore);
					sheet.addCell(cellNewScore);
					sheet.addImage(wi);
					sheet.addCell(cellSmiles);
					sheet.addCell(cellInchi);
				} catch (WriteException e) {
					System.out.println("Could not write excel cell");
					e.printStackTrace();
//					createdResource = false;
//					return;
				}
				
			}
		}
		else {
//			createdResource = false;
//			return;
		}
			
		if(genericDatabase.getResults().size() > 0) {
			// set sheet name (output port) and position
			sheet = workbook.createSheet(genericDatabase.getDatabaseName() + " Results", currentSheet);
			currentSheet++;
			WritableCell headerRank = new Label(0, 0, "Rank", arial12format);
			WritableCell headerID = new Label(1, 0, "ID", arial12format);
			WritableCell headerName = new Label(2, 0, "Compound Name", arial12format);
			WritableCell headerOrigScore = new Label(3, 0, "Original Score", arial12format);
			WritableCell headerStructure = new Label(4, 0, "Structure", arial12format);
			WritableCell headerSmiles = new Label(5, 0, "SMILES", arial12format);
			WritableCell headerInchi = new Label(6, 0, "InChI", arial12format);
			try
			{
				sheet.addCell(headerRank);
				sheet.addCell(headerID);
				sheet.addCell(headerName);
				sheet.addCell(headerOrigScore);
				sheet.addCell(headerStructure);
				sheet.addCell(headerSmiles);
				sheet.addCell(headerInchi);
			} catch (WriteException e) {
				System.out.println("Could not write Excel sheet headers!");
				e.printStackTrace();
//				createdResource = false;
//				return;
			}
			
			int currentRow = 0;
			int currentCol = 0;
			
			// write MassBank results
			for (Result result : genericDatabase.getResults()) {
				//currentRow = counter*4 + 1;
				currentRow++;
				
				currentCol = 0;
				// output is text
				WritableCell cellRank = new Number(currentCol, currentRow, result.getTiedRank(), arial10format);
				currentCol++;
				WritableCell cellID = new Label(currentCol, currentRow, result.getId(), arial10format);
				currentCol++;
				WritableCell cellName = new Label(currentCol, currentRow, result.getName(), arial10format);
				currentCol++;
				WritableCell cellOrigScore = new Number(currentCol, currentRow, result.getScoreShort(), arial10format);
				currentCol++;
				//wi = new WritableImage(4, currentRow, 1, 3, new File(appPath, result.getImagePath()));
				File temp = new File(webRoot, result.getImagePath());
				if(temp.exists()) {
					wi = new WritableImage(currentCol, currentRow, 1, 3, temp);
					currentCol++;
				}
				//wi = new WritableImage(5, currentRow, 1, 3, new File(webRoot, result.getImagePath()));
				
				WritableCell cellSmiles = new Label(currentCol, currentRow, result.getSmiles(), arial10format);
				currentCol++;
				WritableCell cellInchi = new Label(currentCol, currentRow, result.getInchi(), arial10format);
				currentCol++;
				
				try
				{
					sheet.addCell(cellRank);
					sheet.addCell(cellID);
					sheet.addCell(cellName);
					sheet.addCell(cellOrigScore);
					sheet.addImage(wi);
					sheet.addCell(cellSmiles);
					sheet.addCell(cellInchi);
				} catch (WriteException e) {
					System.out.println("Could not write excel cell");
					e.printStackTrace();
//					createdResource = false;
//					return;
				}
				
			}
		}
		else {
//			createdResource = false;
//			return;
		}
		
		if(mfb.getResults().size() > 0) {
			// set sheet name (output port) and position
			sheet = workbook.createSheet("MetFrag Results", currentSheet);
			currentSheet++;
			WritableCell headerRank = new Label(0, 0, "Rank", arial12format);
			WritableCell headerID = new Label(1, 0, "ID", arial12format);
			WritableCell headerName = new Label(2, 0, "Compound Name", arial12format);
			WritableCell headerOrigScore = new Label(3, 0, "Original Score", arial12format);
			WritableCell headerStructure = new Label(4, 0, "Structure", arial12format);
			WritableCell headerSmiles = new Label(5, 0, "SMILES", arial12format);
			WritableCell headerInchi = new Label(6, 0, "InChI", arial12format);
			try
			{
				sheet.addCell(headerRank);
				sheet.addCell(headerID);
				sheet.addCell(headerName);
				sheet.addCell(headerOrigScore);
				sheet.addCell(headerStructure);
				sheet.addCell(headerSmiles);
				sheet.addCell(headerInchi);
			} catch (WriteException e) {
				System.out.println("Could not write Excel sheet headers!");
				e.printStackTrace();
			}
			
			int currentRow = 0;
			int currentCol = 0;
			// write MetFrag results
			for (Result result : mfb.getResults()) {
				//currentRow = counter*4 + 1;
				currentRow++;
				
				currentCol = 0;
				// output is text
				WritableCell cellRank = new Number(currentCol, currentRow, result.getTiedRank(), arial10format);
				currentCol++;
				WritableCell cellID = new Label(currentCol, currentRow, result.getId(), arial10format);
				currentCol++;
				WritableCell cellName = new Label(currentCol, currentRow, result.getName(), arial10format);
				currentCol++;
				WritableCell cellOrigScore = new Number(currentCol, currentRow, result.getScoreShort(), arial10format);
				currentCol++;
				//wi = new WritableImage(4, currentRow, 1, 3, new File(appPath, result.getImagePath()));
				File temp = new File(webRoot, result.getImagePath());
				if(temp.exists()) {
					wi = new WritableImage(currentCol, currentRow, 1, 3, temp);
					currentCol++;
				}
				
				WritableCell cellSmiles = new Label(currentCol, currentRow, result.getSmiles(), arial10format);
				currentCol++;
				WritableCell cellInchi = new Label(currentCol, currentRow, result.getInchi(), arial10format);
				currentCol++;
				
				try
				{
					sheet.addCell(cellRank);
					sheet.addCell(cellID);
					sheet.addCell(cellName);
					sheet.addCell(cellOrigScore);
					sheet.addImage(wi);
					sheet.addCell(cellSmiles);
					sheet.addCell(cellInchi);
				} catch (WriteException e) {
					System.out.println("Could not write excel cell");
					e.printStackTrace();
//					createdResource = false;
//					return;
				}
				
			}
		}
		else {
//			createdResource = false;
//			return;
		}
		
		
		
		// for each workflow output port, create new sheet inside Excel file and store results
//		for (WorkflowOutput port : outputs) {
//			// set sheet name (output port) and position
//			sheet = workbook.createSheet(port.getName(), outputs.indexOf(port));
//			ArrayList<WorkflowOutput> elements = port.getElements();
//			
//			// set header for sheet, name it after output port name 
//			try {
//				WritableFont arial10font = new WritableFont(WritableFont.ARIAL, 10);
//				WritableCellFormat arial10format = new WritableCellFormat(
//						arial10font);
//				arial10font.setBoldStyle(WritableFont.BOLD);
//				Label label = new Label(0, 0, port.getName(), arial10format);
//				sheet.addCell(label);
//			} catch (WriteException we) {
//				we.printStackTrace();
//			}
//			
//			// for all output elements, store their result inside the current sheet
//			// either store the image or the value part of an output 
//			for (int i = 0; i < elements.size(); i++) {
//				WritableCell cell = null;
//				WritableImage wi = null;
//				if(elements.get(i).isImage()) {		// output is image
//					String imgPath = appPath + elements.get(i).getPath();
//					File image = new File(imgPath);
//					// write each image into the second column, leave one row space between them and 
//					// resize the image to 1 column width and 2 rows height
//					wi = new WritableImage(1, (i*3) + 1, 1, 2, image);
//					sheet.addImage(wi);
//				}
//				else if(!elements.get(i).isImage()) {	// output is text
//					cell = new Label(1, i, elements.get(i).getValue());
//					try {
//						sheet.addCell(cell);
//					} catch (WriteException e) {
//						System.out.println("Could not write excel cell");
//						e.printStackTrace();
//					}
//				}
//			}
//		}
		
		// write the Excel file
		try {
			workbook.write();
			workbook.close();
		} catch (WriteException ioe) {
			ioe.printStackTrace();
			createdResource = false;
			return;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			createdResource = false;
			return;
		}
		
//		OutputResource out = new OutputResource();
//		out.setFileName(f.getAbsolutePath());
//		out.setMimeType(mimeType);
		// store the current Excel file as output resource
		XLSResource xls = new XLSResource(ec, resourceName, folder);
		setOutputResource(xls);
		setCreatedResource(Boolean.TRUE);
		
		//this.exporter = new XLSOutputHandler(folder + resourceName);//, FacesContext.getCurrentInstance(), "Results");
	}
	
	/**
	 * Decode HTML-attribute style of color to {@link Color}
	 * 
	 * @param color - color name or #RRGGBB string
	 * @return - color for this value.
	 */
	public Color decode(String color) {
		if (null == color) {
			throw new IllegalArgumentException("NULL_COLOR_PARAMETER_ERROR");
		}
		Color c = null;
		try {
			c = Color.decode(color.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("DECODE_COLOR_PARAMETER_ERROR");
		}
		return c;
	}

	public void changeIonizationListener(ValueChangeEvent event) {
		String newVal = (String) event.getNewValue();
		if(newVal.equals("1") | newVal.equals("pos")) 
			metfragModeSetting = "MetFrag is set to work in <b>positive</b> mode.";
		else if(newVal.equals("-1") | newVal.equals("neg")) 
			metfragModeSetting = "MetFrag is set to work in <b>negative</b> mode.";
		else if(newVal.equals("0"))
			metfragModeSetting = "MetFrag <b>would</b> work in GC-MS mode - <b>defaulting back to positive mode</b>.";
		else metfragModeSetting = "<b>Unknown ionization mode</b>.";
		
		this.effectNote = new Highlight("#FFA500");
	}
	
	public void reset(ActionEvent event) {
		System.out.println("started reset procedures");
		this.newOrder = new ArrayList<ResultExt>();
		this.secondOrder = new ArrayList<ResultExt>();
		this.colorMatrix = null;
		this.colorMatrixAfter = null;
		this.showTable = false;
		this.showResultTable = false;
		//this.useClustering = false;
		
		this.mblb.setOriginalResults(null);
		this.mblb.setQueryResults(null);
		this.mblb.setResults(null);
		this.mblb.setShowResult(false);
		
		this.mfb.setResults(null);
		setShowResultsFragmenter(false);
		setShowResultsDatabase(false);
		setShowTable(false);
		setShowResultTable(false);
		
		this.mfb.setShowResult(false);
		setShowClusterResults(false);
		this.tanimotoClusters = new ArrayList<ResultExtGroupBean>();
		
		this.selectedTab = "0";
		//setMblb(new MassBankLookupBean());
		//setMfb(new MetFragBean());
		System.out.println("finished reset procedures");
	}
	
	public MassBankLookupBean getMblb() {
		return mblb;
	}

	public void setMblb(MassBankLookupBean mblb) {
		this.mblb = mblb;
	}

	public MetFragBean getMfb() {
		return mfb;
	}

	public void setMfb(MetFragBean mfb) {
		this.mfb = mfb;
	}

	public void setNewOrder(List<ResultExt> newOrder) {
		this.newOrder = newOrder;
	}

	public List<ResultExt> getNewOrder() {
		return newOrder;
	}

	public void setColorMatrix(ColorcodedMatrix colorMatrix) {
		this.colorMatrix = colorMatrix;
	}

	public ColorcodedMatrix getColorMatrix() {
		return colorMatrix;
	}

	public void setSecondOrder(List<ResultExt> secondOrder) {
		this.secondOrder = secondOrder;
	}

	public List<ResultExt> getSecondOrder() {
		return secondOrder;
	}

	public void setChartValues(List<double[]> chartValues) {
		this.chartValues = chartValues;
	}

	public List<double[]> getChartValues() {
		return chartValues;
	}

	public void setChartColors(List<Color> chartColors) {
		this.chartColors = chartColors;
	}

	public List<Color> getChartColors() {
		return chartColors;
	}

	public void setShowTable(boolean showTable) {
		this.showTable = showTable;
	}

	public boolean isShowTable() {
		return showTable;
	}

	public void setInputSpectrum(String inputSpectrum) {
		this.inputSpectrum = inputSpectrum;
	}

	public String getInputSpectrum() {
		return inputSpectrum;
	}

	public void setUseClustering(boolean useClustering) {
		this.useClustering = useClustering;
	}

	public boolean isUseClustering() {
		return useClustering;
	}

	public void setShowResultTable(boolean showResultTable) {
		this.showResultTable = showResultTable;
	}

	public boolean isShowResultTable() {
		return showResultTable;
	}

	public void setShowResultsDatabase(boolean showResultsDatabase) {
		this.showResultsDatabase = showResultsDatabase;
	}

	public boolean isShowResultsDatabase() {
		return showResultsDatabase;
	}

	public void setShowResultsFragmenter(boolean showResultsFragmenter) {
		this.showResultsFragmenter = showResultsFragmenter;
	}

	public boolean isShowResultsFragmenter() {
		return showResultsFragmenter;
	}

	public void setShowClusterResults(boolean showClusterResults) {
		this.showClusterResults = showClusterResults;
	}

	public boolean isShowClusterResults() {
		return showClusterResults;
	}

	public void setTanimotoClusters(List<ResultExtGroupBean> tanimotoClusters) {
		this.tanimotoClusters = tanimotoClusters;
	}

	public List<ResultExtGroupBean> getTanimotoClusters() {
		return tanimotoClusters;
	}

	/**
	 * Returns the text effect
	 * 
	 * @return Effect EffectOutputText
	 */
	public Effect getEffectOutputText() {
		effectOutputText = new Highlight("#FFA500");
		effectOutputText.setFired(false);

		return effectOutputText;
	}

	/**
	 * Sets the output text effect
	 * 
	 * @param Effect
	 *            effectOutputText
	 */
	public void setEffectOutputText(Effect effectOutputText) {
		this.effectOutputText = (Highlight) effectOutputText;
	}

	public void setSelectedResult(String selectedResult) {
		this.selectedResult = selectedResult;
	}
	
	public String getSelectedResult() {
		return selectedResult;
	}
	
	public void setSelectedTab(String selectedTab) {
		this.selectedTab = selectedTab;
	}
	
	public String getSelectedTab() {
		return selectedTab;
	}
	
	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
	
	public String getErrorMessage() {
		return errorMessage;
	}
	
	public void setColorMatrixAfter(ColorcodedMatrix colorMatrixAfter) {
		this.colorMatrixAfter = colorMatrixAfter;
	}
	
	public ColorcodedMatrix getColorMatrixAfter() {
		return colorMatrixAfter;
	}
	
	public void setOutputResource(Resource outputResource) {
		this.outputResource = outputResource;
	}
	
	public Resource getOutputResource() {
		return outputResource;
	}
	
	public void setExporter(XLSOutputHandler exporter) {
		this.exporter = exporter;
	}
	
	public XLSOutputHandler getExporter() {
		return exporter;
	}

	public void setSelectedMatrixPanel(String selectedMatrixPanel) {
		this.selectedMatrixPanel = selectedMatrixPanel;
	}

	public String getSelectedMatrixPanel() {
		return selectedMatrixPanel;
	}

	public void setCreatedResource(boolean createdResource) {
		this.createdResource = createdResource;
	}

	public boolean isCreatedResource() {
		return createdResource;
	}

	public int getPercentProgress() {
		return percentProgress;
	}

	public void setPercentProgress(int percentProgress) {
		this.percentProgress = percentProgress;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public void setRunning(boolean isRunning) {
		this.isRunning = isRunning;
	}

	public void setOverallProgress(int overallProgress) {
		this.overallProgress = overallProgress;
	}

	public int getOverallProgress() {
		return overallProgress;
	}

	public void setPercentProgressDatabase(int percentProgressDatabase) {
		this.percentProgressDatabase = percentProgressDatabase;
	}

	public int getPercentProgressDatabase() {
		return percentProgressDatabase;
	}

	public void setPercentProgressFragmenter(int percentProgressFragmenter) {
		this.percentProgressFragmenter = percentProgressFragmenter;
	}

	public int getPercentProgressFragmenter() {
		return percentProgressFragmenter;
	}

	public String getNavigate() {
		return navigate;
	}

	public void setNavigate(String navigate) {
		this.navigate = navigate;
	}

	public void setPercentProgressGlobal(int percentProgressGlobal) {
		this.percentProgressGlobal = percentProgressGlobal;
	}

	public int getPercentProgressGlobal() {
		return percentProgressGlobal;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getStatus() {
		return status;
	}

	public void setEnableStart(boolean enableStart) {
		this.enableStart = enableStart;
	}

	public boolean isEnableStart() {
		return enableStart;
	}

	public void setEffect(Effect effect) {
		this.effect = effect;
	}

	public Effect getEffect() {
		return effect;
	}

	public void setMetfragModeSetting(String metfragModeSetting) {
		this.metfragModeSetting = metfragModeSetting;
	}

	public String getMetfragModeSetting() {
		return metfragModeSetting;
	}

	public void setEffectNote(Highlight effectNote) {
		this.effectNote = effectNote;
	}

	public Highlight getEffectNote() {
		return effectNote;
	}

	public void setUseInChIFiltering(boolean useInChIFiltering) {
		this.useInChIFiltering = useInChIFiltering;
	}

	public boolean isUseInChIFiltering() {
		return useInChIFiltering;
	}

	public void setSelectedSpectralDB(String selectedSpectralDB) {
		this.selectedSpectralDB = selectedSpectralDB;
	}

	public String getSelectedSpectralDB() {
		return selectedSpectralDB;
	}

	public void setAvailableSpectralDBsSI(SelectItem[] availableSpectralDBsSI) {
		this.availableSpectralDBsSI = availableSpectralDBsSI;
	}

	public SelectItem[] getAvailableSpectralDBsSI() {
		return availableSpectralDBsSI;
	}

	public void setGenericDatabase(GenericDatabaseBean genericDatabase) {
		this.genericDatabase = genericDatabase;
	}

	public GenericDatabaseBean getGenericDatabase() {
		return genericDatabase;
	}

	public void setGmdb(GMDBean gmdb) {
		this.gmdb = gmdb;
	}

	public GMDBean getGmdb() {
		return gmdb;
	}

	public void setProps(PropertiesBean props) {
		this.props = props;
	}

	public PropertiesBean getProps() {
		return props;
	}

	public void setCreatedResourceSDF(boolean createdResourceSDF) {
		this.createdResourceSDF = createdResourceSDF;
	}

	public boolean isCreatedResourceSDF() {
		return createdResourceSDF;
	}

	public void setOutputResourceSDF(Resource outputResourceSDF) {
		this.outputResourceSDF = outputResourceSDF;
	}

	public Resource getOutputResourceSDF() {
		return outputResourceSDF;
	}

	public boolean isCreatedResourceSDFDatabase() {
		return createdResourceSDFDatabase;
	}

	public void setCreatedResourceSDFDatabase(boolean createdResourceSDFDatabase) {
		this.createdResourceSDFDatabase = createdResourceSDFDatabase;
	}

	public Resource getOutputResourceSDFDatabase() {
		return outputResourceSDFDatabase;
	}

	public void setOutputResourceSDFDatabase(Resource outputResourceSDFDatabase) {
		this.outputResourceSDFDatabase = outputResourceSDFDatabase;
	}

	public boolean isCreatedResourceSDFFragmenter() {
		return createdResourceSDFFragmenter;
	}

	public void setCreatedResourceSDFFragmenter(boolean createdResourceSDFFragmenter) {
		this.createdResourceSDFFragmenter = createdResourceSDFFragmenter;
	}

	public Resource getOutputResourceSDFFragmenter() {
		return outputResourceSDFFragmenter;
	}

	public void setOutputResourceSDFFragmenter(Resource outputResourceSDFFragmenter) {
		this.outputResourceSDFFragmenter = outputResourceSDFFragmenter;
	}

	public boolean isCreatedResourceSDFUnused() {
		return createdResourceSDFUnused;
	}

	public void setCreatedResourceSDFUnused(boolean createdResourceSDFUnused) {
		this.createdResourceSDFUnused = createdResourceSDFUnused;
	}

	public Resource getOutputResourceSDFUnused() {
		return outputResourceSDFUnused;
	}

	public void setOutputResourceSDFUnused(Resource outputResourceSDFUnused) {
		this.outputResourceSDFUnused = outputResourceSDFUnused;
	}

}
