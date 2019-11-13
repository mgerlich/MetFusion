/**
 * created by Michael Gerlich on May 18, 2010
 * last modified May 18, 2010 - 12:41:12 PM
 * email: mgerlich@ipb-halle.de
 */
package de.ipbhalle.MassBank;

import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.event.ValueChangeEvent;
import javax.faces.model.SelectItem;
import javax.faces.model.SelectItemGroup;

import net.sf.jniinchi.INCHI_RET;

import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.inchi.InChIGenerator;
import org.openscience.cdk.inchi.InChIGeneratorFactory;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

import chemaxon.descriptors.ECFP;

import de.ipbhalle.metfusion.utilities.MassBank.MassBankUtilities;
/**
 * enable code and include de.ipbhalle.metfusion.utilities.chemaxon.ChemAxonUtilities in build path to access
 * ChemAxon provided functionality. [1/3]
 
import de.ipbhalle.metfusion.utilities.chemaxon.ChemAxonUtilities;
*/
import de.ipbhalle.metfusion.web.controller.GenericDatabaseBean;
import de.ipbhalle.metfusion.web.controller.MetFragBean;
import de.ipbhalle.metfusion.web.controller.PropertiesBean;
import de.ipbhalle.metfusion.wrapper.Result;

import massbank.GetConfig;
import massbank.GetInstInfo;
import massbank.MassBankCommon;

//@ManamassBankLookupBeangedBean(eager=true)
@ManagedBean(name="databaseBean")
@SessionScoped
//@CustomScoped(value = "#{window}")
public class MassBankLookupBean extends Thread implements Runnable, Serializable, GenericDatabaseBean {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private String databaseName = "MassBank";
	
	private String serverUrl;
	private MassBankCommon mbCommon;
	private GetConfig config;
	private GetInstInfo instInfo;
	private Map<String, List<String>> instruments;
	
	private int limit = 100;
	private int cutoff = 5;
	
	private String[] selectedInstruments;
	private List<String[]> selectedGroupInstruments;
	private SelectItem[] insts;
	private List<SelectItemGroup> groupInstruments;
	private List<SelectItem[]> instTest;
	
	// boolean indicators to mimic selection of different instrument types
	// and combinations -> LC-ESI-... or only EI-...
	private boolean useEIOnly = false;
	private boolean useESIOnly = true;
	private boolean useOtherOnly = false;
	private boolean useLC = false;
	private boolean useGC = false;
	private static final String EI = "EI";
	private static final String ESI = "ESI";
	private static final String OTHER = "Others";
	 /** EI, ESI, Other */
    public int NUM_INST_GROUPS = 3;
    private boolean presentEI = Boolean.FALSE;
    private boolean presentESI = Boolean.FALSE;
    private boolean presentOther = Boolean.FALSE;
    private int indexEI = 0;
    private int indexESI = 1;
    private int indexOther = 2;
	private Map<String, List<String>> instGroups;
	private static final String SESSIONMAPKEYINSTRUMENTS = "instruments";
	private static final String SELECT = "Select";
	private static final String DESELECT = "Deselect";
	private String linkGroupEI = SELECT + " " + EI;
	private String linkGroupESI = DESELECT + " " + ESI;
	private String linkGroupOTHER = SELECT + " " + OTHER;
	private Map<String, String> instrumentToGroup;
	
	private String selectedIon = "1";
	private SelectItem[] ionisations = {new SelectItem("1", "positive"), new SelectItem("-1", "negative"), new SelectItem("0", "both")};
	
	public static final String varOTHER = "other";
	public static final String massbankJP = "http://www.massbank.jp/";
	public static final String massbankMSBI = "http://msbi.ipb-halle.de/MassBank/";
	public static final String massBankEU = "http://www.massbank.eu/MassBank/";
	
	private SelectItem[] availableServers = {new SelectItem(massbankJP, "MassBank JP"), 
			new SelectItem(massBankEU, "MassBank EU"),
			new SelectItem(massbankMSBI, "MassBank IPB"), 
//			new SelectItem("http://pfos.jp/MassBank/", "Pfos"),
			new SelectItem(varOTHER, varOTHER)};
	private String selectedServer = "";
	
	/** MassBank MS levels */
	public static final String ms1 = "MS";
	public static final String ms2 = "MS2";
	public static final String ms3 = "MS3";
	public static final String ms4 = "MS4";
	public static final String msAll = "all";
	public static final String msNA = "N/A";	// N/A ms level
	private SelectItem[] availableMSLevels = {new SelectItem(msAll, msAll), new SelectItem(ms1, ms1), new SelectItem(ms2, ms2), 
			new SelectItem(ms3, ms3), new SelectItem(ms4, ms4)};	// available MS levels
	private String[] selectedMSLevel = {msAll, ms1, ms2, ms3, ms4};	// selected MS levels 
	private final String parameterMSlvl = "&ms=";		// MassBank MS level parameter
	private String msLevel = "&ms=all&ms=MS1&ms=MS2&ms=MS3&ms=4&ms=N%2FA";						// formatted MS level String, last == N/A
	
	/** Allow to use another server besides the default one. */
	private boolean selectOtherServer = Boolean.FALSE;
	
	/** A user-defined web adress for another MassBank server instance. */
	private String otherServer = "";
	
	/** previously selected server in case of fall back */
	private String previousServer = "";
	
	/** Allow to specify another server besides the default ones. */
	private boolean allowOtherServer = Boolean.FALSE;
	
	private String inputSpectrum = "273.096 22\n289.086 107\n290.118 14\n291.096 999\n292.113 162\n293.054 34\n579.169 37\n580.179 15";
	
	/** MassBank parameter qmz which denotes the mz values from the inputSpectrum that should be highlighted inside a MassBank record.	 */
	private String qmz;
	
	private List<String> queryResults;
	private boolean showResult;
	private List<Result> results;
	private List<String> originalResults;
	private String missingEntriesNote = "Note: Some entries from the original MassBank query are left out because of missing structure information!";
	private boolean showNote = Boolean.FALSE;
	
	/** indicator for filtering duplicate entries */
	private boolean uniqueInchi = Boolean.FALSE;
	private List<Result> unused;
	
	//private String cacheMassBank = "/vol/massbank/Cache/";
	
	private static final String fileSeparator = System.getProperty("file.separator");
	private static final String os = System.getProperty("os.name");
	private static final String currentDir = System.getProperty("user.dir");
	private static final String tempDir = System.getProperty("java.io.tmpdir");
	private String cacheMassBank = "";
	public final String DEFAULT_CACHE = tempDir;
	public final String DEFAULT_CACHE_LINUX = tempDir;	//"/vol/massbank/Cache/";
	
	private static final String RECORDS = "records";
	private static final String MOL = "mol";
	
    private String sessionPath;
    
    private boolean brokenMassBank = false;
    
    /**
     * nur für Auswertung gedacht!
     */
    private String currentRecord = "";
    
    private int searchProgress = 0;
    private int searchCounter = 0;
    private boolean isRunning = false;
    
    private boolean done = Boolean.FALSE;
    public synchronized void notifyDone() {
    	done = Boolean.TRUE;
    	notifyAll();
    }
    
	public MassBankLookupBean() {
		this("http://localhost/MetFusion/");			// create instance with default massbank server JAPAN
	}
	
	public MassBankLookupBean(String serverUrl) {
		this.setMbCommon(new MassBankCommon());		// only create new MassBankCommon once
		try {
			setupMassBank(serverUrl);
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	private void setupMassBank(String serverUrl) {
		// retrieve application scoped PropertiesBean
		PropertiesBean pb = (PropertiesBean) FacesContext.getCurrentInstance().getExternalContext().getApplicationMap().get("propertiesBean");
		String propServerUrl = pb.getProperty("serverURL");		// read massbank server from properties file
		// if another server is not selected and if this property is set, use the designated server rather the default
		if(!allowOtherServer && propServerUrl != null && !propServerUrl.isEmpty())	{			
			serverUrl = propServerUrl;
		}
		
		String propCacheDir = pb.getProperty("databaseCache");	// read cache directory from properties file
		if(propCacheDir != null && !propCacheDir.isEmpty())		// if this property is set, use the designated directory rather the default
			cacheMassBank = propCacheDir;
		
        this.serverUrl = (serverUrl.isEmpty() | !serverUrl.startsWith("http://") ? massbankJP : serverUrl);
        
        if(previousServer.equals(this.serverUrl)) {	// if previous server was the same
        	SelectItem[] availableServers = getAvailableServers();
	    	boolean standard = false;
	    	for (int i = 0; i < availableServers.length; i++) {
				if(availableServers[i].getValue().equals(getServerUrl())) {
					setSelectedServer((String) availableServers[i].getValue());
					setSelectOtherServer(Boolean.FALSE);	// disable input field for other server
		        	setOtherServer("");
					standard = true;
					break;
				}
			}
	    	if(!standard) {		// server is other
	    		setSelectedServer(MassBankLookupBean.varOTHER);
	    		setSelectOtherServer(Boolean.TRUE);	// enable input field for other server
	    		setOtherServer(this.serverUrl);
	    	}
        	return;									// skip instrument parsing
        }
        
    	this.setConfig(new GetConfig(this.serverUrl));
    	this.setInstInfo(new GetInstInfo(this.serverUrl));
        
        // fall back to default server if provided server is no correct MassBank instance
        if(getInstInfo().getName() == null || getInstInfo().getName().length == 0 || getInstInfo().getName()[0].length() == 0) {
        	this.serverUrl = massbankJP;
        	this.setConfig(new GetConfig(this.serverUrl));
        	this.setInstInfo(new GetInstInfo(this.serverUrl));
        	
        	setSelectedServer((String) availableServers[0].getValue());		// default to massbank japan
        	setSelectOtherServer(Boolean.FALSE);	// disable input field for other server
        	setOtherServer("");
        	
        	if(previousServer.equals(this.serverUrl))	// if previous server was the same
        		return;									// skip instrument parsing
        }
        this.setInstruments(this.instInfo.getTypeGroup());
        showResult = false;
        System.out.println("serverUrl: " + this.serverUrl);
        previousServer = this.serverUrl;		// update previous server to current one
        
        // set selected server
        for (int i = 0; i < availableServers.length; i++) {
			if(availableServers[i].getValue().equals(getServerUrl())) {
				setSelectedServer((String) availableServers[i].getValue());
				setSelectOtherServer(Boolean.FALSE);	// disable input field for other server
	        	setOtherServer("");
				break;
			}
		}
        
        if(allowOtherServer) {
	        // set up server Url 
	    	SelectItem[] availableServers = getAvailableServers();
	    	boolean standard = false;
	    	for (int i = 0; i < availableServers.length; i++) {
				if(availableServers[i].getValue().equals(getServerUrl())) {
					setSelectedServer((String) availableServers[i].getValue());
					setSelectOtherServer(Boolean.FALSE);	// disable input field for other server
		        	setOtherServer("");
					standard = true;
					break;
				}
			}
	    	if(!standard) {		// server is other
	    		setSelectedServer(MassBankLookupBean.varOTHER);
	    		setSelectOtherServer(Boolean.TRUE);	// enable input field for other server
	    		setOtherServer(this.serverUrl);
	    	}
        }
        
        Map<String, List<String>> instGroup = instInfo.getTypeGroup();
        // store instrument groups with group identifier
        this.instGroups = instGroup;
        instTest = new ArrayList<SelectItem[]>();
        
        Iterator<String> it = instGroup.keySet().iterator();
        int counter = 0;
        StringBuilder sb = new StringBuilder();
        List<SelectItemGroup> sig = new ArrayList<SelectItemGroup>();
        this.selectedGroupInstruments = new ArrayList<String[]>();
        this.instrumentToGroup = new HashMap<String, String>();
        
        // reset presence indicators for instrument groups
        presentEI = Boolean.FALSE;
        presentESI = Boolean.FALSE;
        presentOther = Boolean.FALSE;
        
        // iterate over instrument groups
        while(it.hasNext()) {
        	String next = it.next();
        	SelectItemGroup sigcur = new SelectItemGroup(next);
        	List<String> items = instGroup.get(next);	// retrieve instruments from current instrument group
        	String[] instruments = new String[items.size()];
        	
        	// add ESI instruments as default selected group to sessionmap
        	if(next.equals(ESI))
        		FacesContext.getCurrentInstance().getExternalContext().getSessionMap().put(SESSIONMAPKEYINSTRUMENTS, items);
        	
        	if(next.equals(EI) && items.size() > 0) {
        		presentEI = Boolean.TRUE;
        		indexEI = counter;
        	}
        	else if(next.equals(EI) && (items == null || items.size() == 0))
        		presentEI = Boolean.FALSE;
        	
        	if(next.equals(ESI) && items.size() > 0) {
        		presentESI = Boolean.TRUE;
        		indexESI = counter;
        	}
        	else if(next.equals(ESI) && (items == null || items.size() == 0))
        		presentESI = Boolean.FALSE;
        	
        	if(next.equals(OTHER) && items.size() > 0) {
        		presentOther = Boolean.TRUE;
        		indexOther = counter;
        	}
        	else if(next.equals(OTHER) && (items == null || items.size() == 0))
        		presentOther = Boolean.FALSE;
        	
        	SelectItem[] si = new SelectItem[items.size()];
        	for (int i = 0; i < si.length; i++) {
        		String s = items.get(i);
				si[i] = new SelectItem(s, s);
				sb.append(s).append(",");
				
				// add instrument to list for corresponding group
				if(s.contains(ESI))
					instruments[i] = s;		// preselect all ESI instruments
				else instruments[i] = "";	// deselect all remaining instruments (EI, Others)
				
				// add instrument and corresponding group information
				instrumentToGroup.put(s, next);
			}
        	instTest.add(si);
        	selectedGroupInstruments.add(instruments);
        	
        	sigcur.setSelectItems(si);
        	sig.add(sigcur);

        	counter++;
        	this.instruments.put(next, items);
        }
        
        // keep number of instrument groups for current database
        NUM_INST_GROUPS = counter;
//        FacesContext.getCurrentInstance().renderResponse();
        
        // check MassBank availability - check if all instrument groups are present - EI, ESI, Other
        if(instGroup.keySet().size() < NUM_INST_GROUPS)
        	this.brokenMassBank = true;
        
        this.groupInstruments = sig;

	}
	
	public void loadInstruments(String[] instruments) {
		List<String[]> selected = new ArrayList<String[]>();
		Set<String> groups = instGroups.keySet();
		for (Iterator<String> it = groups.iterator(); it.hasNext();) {
			String grp = it.next();
			selected.add(new String[instGroups.get(grp).size()]);
		}
		
		Map<String, List<String>> loaded = new HashMap<String, List<String>>();
		for (int i = 0; i < instruments.length; i++) {
			String group = instrumentToGroup.get(instruments[i]);
			if(!loaded.containsKey(group)) {
				List<String> list = new ArrayList<String>();
				list.add(instruments[i]);
				loaded.put(group, list);
			}
			else {
				List<String> list = loaded.get(group);
				list.add(instruments[i]);
				loaded.put(group, list);
			}
		}
		
		Set<String> keys = loaded.keySet();
		for (Iterator<String> it = keys.iterator(); it.hasNext();) {
			String key = it.next();
			List<String> insts = loaded.get(key);
			int slot = 0;
			if(key == null)
				continue;
			
			if(key.equals(EI)) {
				slot = indexEI;
			}
			else if(key.equals(ESI)) {
				slot = indexESI;
			}
			else if(key.equals(OTHER)) {
				slot = indexOther;
			}
			else {
				System.err.println("loadInstruments - Could not find matching Instrument Group!");
				return;
			}
			
			String[] newInstruments = selected.get(slot);
			for (int i = 0; i < insts.size(); i++) {
				newInstruments[i] = insts.get(i);
			}
			selectedGroupInstruments.set(slot, newInstruments);
		}
	}
	
	public void changeMSLevels(ValueChangeEvent event) {
		String[] newVal = ((String[]) event.getNewValue());
		this.msLevel = "";
		for (int i = 0; i < newVal.length; i++) {
			msLevel += parameterMSlvl + newVal[i];
		}
	}
	
	public void changeDatabase(ValueChangeEvent event) {
		
		String oldVal = ((String) event.getOldValue()).trim();
		String newVal = ((String) event.getNewValue()).trim();
		
		// skip re-loading of values after constructor setup
		if((oldVal == null | oldVal.isEmpty()) && newVal.equals(massbankJP) && serverUrl.equals(massbankJP))
			return;
		
		if(newVal == null | newVal.isEmpty() | newVal.length() == 0)		// default back to massbank.jp
			newVal = massbankJP;	
		
		if(newVal.equals(varOTHER) && !isSelectOtherServer())	{
			setSelectOtherServer(Boolean.TRUE);	// enable text input field it other is selected
			return;			// skip new server lookup before something was entered
		}
		else if(newVal.equals(varOTHER) && isSelectOtherServer())	{
			newVal = otherServer.trim();			// use value from text input field it other is selected
		}
		//else setSelectOtherServer(Boolean.FALSE);	// otherwise disable it
		
		System.out.println("old DB -> " + oldVal);
		System.out.println("new DB -> " + newVal);
		if(!newVal.startsWith("http://"))
			newVal = "http://" + newVal;
		
		if(!newVal.endsWith("/"))
			newVal = newVal + "/";
		
		if(!newVal.equals(oldVal)) {	// only parse MassBank new if new server is given
			//this.serverUrl = newVal;
			setAllowOtherServer(Boolean.TRUE);
			try {
				URL url = new URL(newVal);
				System.out.println("url -> " + url.toExternalForm());
				setupMassBank(url.toExternalForm());
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void changeInstruments(ValueChangeEvent event) {
		UIComponent source = event.getComponent();
		Map<String, Object> attr = source.getAttributes();
		String group = (String) attr.get("attrGroup");
		//System.out.println("attribute group -> " + group);
		
		int numSelected = 0;
		List<String[]> currentSelected = getSelectedGroupInstruments();
		List<String> remaining = new ArrayList<String>();
		for (int i = 0; i < currentSelected.size(); i++) {
			String[] temp = currentSelected.get(i);
			for (int j = 0; j < temp.length; j++) {
				if(temp[j] != null && !temp[j].isEmpty()) {
					remaining.add(temp[j]);
					numSelected++;
				}
			}
		}
		
		String[] newInstruments = (String[]) event.getNewValue();
		String lastInst = "";
		if(remaining.size() == 1 && newInstruments.length == 0) {
			numSelected = 0;	// reset selected instruments to 0 as the last instrument was deselected
			lastInst = remaining.get(remaining.size() - 1);
		}
		
		int slot = 0;
		String grp = "";
		if(group.equals(EI)) {
			slot = indexEI;
			grp = EI;
		}
		else if(group.equals(ESI)) {
			slot = indexESI;
			grp = ESI;
		}
		else if(group.equals(OTHER)) {
			slot = indexOther;
			grp = OTHER;
		}
		else {
			System.err.println("Could not find matching Instrument Group!");
			return;
		}
		
		if(numSelected == 0 && newInstruments.length == 0) {	// no instrument was selected!
			System.out.println("no instruments selected!!!");
			newInstruments = new String[1];
			//System.out.println("grp -> " + grp);
			int idx = instGroups.get(grp).indexOf(lastInst);
			newInstruments[0] = instGroups.get(grp).get(idx);	// FORCE selection of at least one instrument!
		}
		else if(newInstruments.length > 0) {
			System.out.println("new instrument(s) selected!!!");
			//System.out.println("grp -> " + grp);
		}

		selectedGroupInstruments.set(slot, newInstruments);
		setSelectedInstruments(newInstruments);
		//FacesContext.getCurrentInstance().renderResponse();
		//collectInstruments();
	}
	
	public void collectInstruments() {
		List<String> tempInstruments = new ArrayList<String>();
		List<String[]> currentSelected = getSelectedGroupInstruments();
		for (int i = 0; i < currentSelected.size(); i++) {
			String[] temp = currentSelected.get(i);
			for (int j = 0; j < temp.length; j++) {
				//System.out.println("currentSelected -> " + temp[j]);
				if(!temp[j].isEmpty())
					tempInstruments.add(temp[j]);
			}
		}
		
		String[] collected = new String[tempInstruments.size()];
		for (int i = 0; i < collected.length; i++) {
			collected[i] = tempInstruments.get(i);
			//System.out.println("collected [" + i + "] -> " + collected[i]);
		}
		
		//System.out.println("collected.length -> " + collected.length);
		Set<String> instKeys = instGroups.keySet();
		Iterator<String> it = instKeys.iterator();
		String firstGroup = it.next();
		if(collected.length == 0) {
			collected = new String[1];
			collected[0] = instGroups.get(firstGroup).get(0);
        	String errMessage = "Error - no instruments were selected!";
            System.err.println(errMessage);
            selectedGroupInstruments.set(0, collected);
//            FacesContext fc = FacesContext.getCurrentInstance();
//            FacesMessage curentMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, errMessage, errMessage);
//            fc.addMessage("inputForm:errMsgs", curentMessage);
//            setSelectedInstruments(collected);
//            
//            return;
        }
		
		setSelectedInstruments(collected);
		//FacesContext.getCurrentInstance().renderResponse();
	}
	
	/**
	 * Wrapper method to create commandLink text value 
	 * matching the corresponding state of an instrument
	 * group. The resulting value is either <b>Select groupname</b>
	 * or <b>Deselect groupname</b>.
	 * 
	 * @param group - the name of the group
	 * @param checked - the boolean indicating if a group should be
	 * selected <code>true</code> or not <code>false</code>.
	 * @return a String in the form of either <b>Select groupname</b>
	 * or <b>Deselect groupname</b>.
	 */
	private String generateLinkText(String group, boolean checked) {
		if(checked)
			return SELECT + " "  + group;
		else return DESELECT + " " + group;
	}
	
	public void toggleInstrumentGroup(ActionEvent event) {
		FacesContext context = FacesContext.getCurrentInstance();
		Map<String, String> requestMap = context.getExternalContext().getRequestParameterMap();
		String group = requestMap.get("group").toString();
		//System.out.println("group -> " + group);
		
		int slot = 0;
		boolean check = false;
		
		if(group.equals(EI)) {
			slot = indexEI;
			check = !this.useEIOnly;	// switch from off to on as EI was not preselected before
			this.useEIOnly = check;
			this.linkGroupEI = generateLinkText(group, !check);
		}
		else if(group.equals(ESI)) {
			slot = indexESI;
			check = !this.useESIOnly;	// switch from on to off as ESI was preselected before
			this.useESIOnly = check;
			this.linkGroupESI = generateLinkText(group, !check);
		}
		else if(group.equals(OTHER)) {
			slot = indexOther;
			check = !this.useOtherOnly;	// switch from off to on as Other was not preselected before
			this.useOtherOnly = check;
			this.linkGroupOTHER = generateLinkText(group, !check);
		}
		else {
			System.err.println("Could not find matching Instrument Group!");
			return;
		}
		//FacesContext.getCurrentInstance().renderResponse();
		
		//System.out.println("check -> " + check);
		List<String> instruments = instGroups.get(group);
		String[] newInstruments = new String[instruments.size()];
		for (int i = 0; i < newInstruments.length; i++) {
			if(check)								// let only be group instruments be preselected
				newInstruments[i] = instruments.get(i);
			else newInstruments[i] = "";		// let only be group instruments be deselected
		}
		
		selectedGroupInstruments.set(slot, newInstruments);
		collectInstruments();
	}
	
	@Override
	public void run() {
		// submit(null);
		setDone(Boolean.FALSE);
		setRunning(Boolean.TRUE);
		
		searchProgress = 0;	// reset progress indicator
		String typeName = MassBankCommon.CGI_TBL[MassBankCommon.CGI_TBL_NUM_TYPE][MassBankCommon.CGI_TBL_TYPE_SEARCH];
		StringBuilder sb = new StringBuilder();
		sb.append("&INST=");
		if (selectedInstruments != null && selectedInstruments.length > 0) { // use
																				// chosen
																				// instruments
			for (int i = 0; i < selectedInstruments.length; i++) {
				sb.append(selectedInstruments[i]).append(",");
			}
		} else { // use all available instruments if none selected as none is
					// prohibited
			if (insts != null && insts.length > 0) {
				for (int i = 0; i < insts.length; i++) {
					sb.append(insts[i].getLabel()).append(",");
				}
			} else {
				this.queryResults = new ArrayList<String>();
				return;
			}
		}

		String inst = sb.toString();
		if (inst.endsWith(",")) // remove trailing comma
			inst = inst.substring(0, inst.length() - 1);

		// set ionization mode to positive if none selected
		if (selectedIon == null || selectedIon.isEmpty() || selectedIon.length() == 0)
			selectedIon = (String) ionisations[0].getValue();

		/**
		 * build up parameter string for MassBank search
		 */
		String ion = "&ION=" + selectedIon;
		inst += ion;
		String paramPeak = formatPeaks();
		String param = "quick=true&CEILING=1000&WEIGHT=SQUARE&NORM=SQRT&START=1&TOLUNIT=unit"
				+ "&CORTYPE=COSINE&FLOOR=0&NUMTHRESHOLD=3&CORTHRESHOLD=0.8&TOLERANCE=0.3"
				+ "&CUTOFF=" + cutoff + "&NUM=0&VAL=" + paramPeak.toString();
//		String param = "qpeak=" +paramPeak.toString() + "&CUTOFF=" + cutoff + "&num=20&type=quick&searchType=peak&sortKey=name" +
//				"&sortAction=1&pageNo=1&exec=" +
//				"&inst_grp=ESI&inst=CE-ESI-TOF&inst=ESI-FTICR&inst=ESI-ITFT&inst=HPLC-ESI-TOF&inst=LC-ESI-IT&inst=LC-ESI-ITFT&inst=LC-ESI-ITTOF" +
//				"&inst=LC-ESI-Q&inst=LC-ESI-QFT&inst=LC-ESI-QIT&inst=LC-ESI-QQ&inst=LC-ESI-QTOF&inst=LC-ESI-TOF&inst=UPLC-ESI-QTOF";
////				"&ms=all&ms=MS&ms=MS1&ms=MS2&ms=MS3&ms=MS4&ms=N%2FA" +
////				"&ion=1";
		param += msLevel;	// add MS level information
		param += inst;	// append ionization mode
		System.out.println(param);

		// retrieve result list
		ArrayList<String> result = mbCommon.execMultiDispatcher(serverUrl, typeName, param);

		// only provide non-Hill records to result set
		this.queryResults = new ArrayList<String>();
		/**
		 * add all spectra for evaluation to show correct working on complete
		 * database
		 */
		queryResults.addAll(result);

		// if there are no entries after filtering, add all filtered entries
		// back
		if (queryResults.size() == 0) {
			this.done = Boolean.TRUE;
			return;
		}

		this.originalResults = result;
		this.unused = new ArrayList<Result>();

		// this.queryResults = result;
		this.showResult = true;
		System.out.println("MassBank results#: " + result.size() + "\n");

		wrapResults();
		
		setRunning(false);
		notifyDone();
	}
	
//	public void start() {
//            t.start();
//	}
	
	public void submit(ActionEvent event) {
//		FacesContext fc = FacesContext.getCurrentInstance();
//        ELResolver el = fc.getApplication().getELResolver();
//        ELContext elc = fc.getELContext();
//        ServletContext sc = (ServletContext) fc.getExternalContext().getContext();
//        System.out.println(sc.getContextPath());
        
        /**
         * 2 Wege um an einen Bean zu kommen, beide funktionieren !!!
         */
        //MetFragBean mfb = (MetFragBean) el.getValue(elc, null, "metFragBean");
        //MetFragBean obj = (MetFragBean) elc.getELResolver().getValue(elc, null, "metFragBean");
        /**
         * 
         */
		searchProgress = 0;	// reset progress indicator
        String typeName = MassBankCommon.CGI_TBL[MassBankCommon.CGI_TBL_NUM_TYPE][MassBankCommon.CGI_TBL_TYPE_SEARCH];
        StringBuilder sb = new StringBuilder();
        sb.append("&INST=");
        if(selectedInstruments != null && selectedInstruments.length > 0) {	// use chosen instruments
        	for (int i = 0; i < selectedInstruments.length; i++) {
    			sb.append(selectedInstruments[i]).append(",");
    		}
        }
        else {	// use all available instruments if none selected as none is prohibited
        	for (int i = 0; i < insts.length; i++) {
				sb.append(insts[i].getLabel()).append(",");
			}
        }
        
        String inst = sb.toString();
        if(inst.endsWith(","))		// remove trailing comma
        	inst = inst.substring(0, inst.length() - 1);
        
        // set ionization mode to positive if none selected
        if(selectedIon == null || selectedIon.isEmpty() || selectedIon.length() == 0)
        	selectedIon = (String) ionisations[0].getValue();
        
        /**
         * build up parameter string for MassBank search
         */
        String ion = "&ION=" + selectedIon;
        inst += ion;
        
        //String paramPeak = "273.096,22@289.086,107@290.118,14@291.096,999@292.113,162@293.054,34@579.169,37@580.179,15";
        String paramPeak = formatPeaks();
		String param = "quick=true&CEILING=1000&WEIGHT=SQUARE&NORM=SQRT&START=1&TOLUNIT=unit"
				+ "&CORTYPE=COSINE&FLOOR=0&NUMTHRESHOLD=3&CORTHRESHOLD=0.8&TOLERANCE=0.3"
				+ "&CUTOFF=" + cutoff + "&NUM=0&VAL=" + paramPeak.toString();
		param += msLevel;	// add MS level information
		param += inst;		// append ionization mode
		System.out.println(param);
		/**
		 * 
		 */
		
		// retrieve result list
		ArrayList<String> result = mbCommon.execMultiDispatcher(serverUrl, typeName, param);
		
		this.queryResults = new ArrayList<String>();
		this.queryResults.addAll(result);

		if(queryResults.size() == 0)
			return;
		
		this.originalResults = result;
		this.unused = new ArrayList<Result>();
		
		this.showResult = true;
		System.out.println("MassBank results#: " + result.size() + "\n");
		
		wrapResults();
	}
	
	public void instrumentListener(ValueChangeEvent event) {
		System.out.println("old -> " + event.getOldValue());
		System.out.println("new -> " + event.getNewValue());
		if(event.getNewValue() instanceof String[]) {
			System.out.println("new value == string[]");
			String[] newVals = (String[]) event.getNewValue();
			int counter = 0;
			this.selectedInstruments = new String[this.insts.length];
			for (int i = 0; i < newVals.length; i++) {
				System.out.println(newVals[i]);
				if(newVals[i].contains(EI)) {
					System.out.println("newVals contains EI");
					List<String> insts = instGroups.get(EI);
					System.out.println("#insts -> " + insts.size());
					for (String inst : insts) {
						this.selectedInstruments[counter] = inst;
						counter++;
					}
				}
				if(newVals[i].contains(ESI)) {
					System.out.println("newVals contains ESI");
					List<String> insts = instGroups.get(ESI);
					System.out.println("#insts -> " + insts.size());
					for (String inst : insts) {
						this.selectedInstruments[counter] = inst;
						counter++;
					}
				}
				if(newVals[i].contains(OTHER)) {
					System.out.println("newVals contains Other");
					List<String> insts = instGroups.get(OTHER);
					System.out.println("#insts -> " + insts.size());
					for (String inst : insts) {
						this.selectedInstruments[counter] = inst;
						counter++;
					}
				}
			}
		}
	}
	
	/**
	 * 
	 * @param param - a fully functional and qualified MassBank QuickSearch parameter string
	 */
	public void runQuery(String param) {
		String typeName = MassBankCommon.CGI_TBL[MassBankCommon.CGI_TBL_NUM_TYPE][MassBankCommon.CGI_TBL_TYPE_SEARCH];
		// retrieve result list
		ArrayList<String> result = mbCommon.execMultiDispatcher(serverUrl, typeName, param);
		this.originalResults = result;
	}
	
	/**
	 * Updates counter for progress bar.
	 */
	public void updateSearchProgress() {
		int maximum = this.queryResults.size();
		int border = (limit >= maximum) ? maximum : limit;
		float result = (((float) searchCounter / (float) border) * 100f);
		this.searchProgress = Math.round(result);
		//System.out.println("Called updateSearchProgress -> " + searchProgress);
		
		// Ensure the new percent is within the valid 0-100 range
        if (searchProgress < 0) {
        	searchProgress = 0;
        }
        if (searchProgress > 100) {
        	searchProgress = 100;
        }
        
//        renderer.render(PUSH_GROUP);
	}
	
	private void wrapResults() {
//		FacesContext fc = FacesContext.getCurrentInstance();
//	    HttpSession session = (HttpSession) fc.getExternalContext().getSession(false);
//	    String sessionString = session.getId();
//	    ServletContext scontext = (ServletContext) fc.getExternalContext().getContext();
//	    String webRoot = scontext.getRealPath(sep);
	    
		//String currentFolder = "";	//webRoot + sep + "temp" + sep + sessionString + sep;
		String relImagePath = getSessionPath(); 	//sep + "temp" + sep + sessionString + sep;
		String tempPath = relImagePath.substring(relImagePath.indexOf("/temp"));
		if(!tempPath.endsWith("/"))
			tempPath += "/";
		
		File dir = null;
		File cache = null;
		if(!cacheMassBank.isEmpty()) {	// cache directory has been set
        	dir = new File(cacheMassBank);
        }
        else {
        	if(os.startsWith("Windows"))
    			dir = new File(DEFAULT_CACHE);
    		else
    			dir = new File(DEFAULT_CACHE_LINUX);
        }
		
        List<Result> results = new ArrayList<Result>();
        List<String> duplicates = new ArrayList<String>();

        MassBankUtilities mbu = new MassBankUtilities(serverUrl, cacheMassBank);
        /**
         * enable code and include de.ipbhalle.metfusion.utilities.chemaxon.ChemAxonUtilities in build path to access
         * ChemAxon provided functionality. [2/3]
         
        ChemAxonUtilities cau = null;	// instantiate ChemAxon utilities only when appropriate Fingerprinter is used
        boolean useChemAxon = Boolean.FALSE;
//        String test = "ECFP";
//        //if(getFingerprinter().equals(Fingerprints.ECFP)) {
//        if(test.equals("ECFP")) {
//        	cau = new ChemAxonUtilities(Boolean.FALSE);
//        	useChemAxon = Boolean.TRUE;
//        }
//        //else if(getFingerprinter().equals(Fingerprints.FCFP)) {
//        else if(test.equals(Fingerprints.FCFP.toString())) {
//        	cau = new ChemAxonUtilities(Boolean.TRUE);
//        	useChemAxon = Boolean.TRUE;
//        }
        */
        
        InChIGeneratorFactory igf = null;
        try {
			igf = InChIGeneratorFactory.getInstance();
		} catch (CDKException e) {
			// TODO Auto-generated catch block
			// no inchi generation possible
			// rely on information stored in MassBank records
			System.err.println("Error creating InChIGeneratorFactory instance!");
			System.err.println("If InChI-filter is enabled, it will now be disabled!");
			if(isUniqueInchi())
				setUniqueInchi(Boolean.FALSE);
		}
		Map<String, String> inchiMap = new HashMap<String, String>();	// maps InChI-Key 1 onto InChI
		
        String name = "";
        String id = "";
        double score = 0.0d;
        int numHits = 0;
        String site = "";
        String sumFormula = "";
        
        int limitCounter = 0;
        int resultLimit = (limit >= queryResults.size()) ? queryResults.size() : limit;
        for(int i = 0; i < queryResults.size(); i++) {
            String s = queryResults.get(i);
            this.searchCounter = limitCounter;
            updateSearchProgress();	// update progress bar
            
            /**
             *  create results only till the given limit
             */
            if(limitCounter == resultLimit) {
            	this.searchProgress = 100;
            	//updateSearchProgress();	// update progress bar
            	break;
            }

            String[] split = s.split("\t");
            if(split.length == 6) {
                // name; instrument
                // id
                // ionization mode
                // sum formula
                // score
                // site
                name = split[0].substring(0, split[0].indexOf(";"));
                id = split[1].trim();
                sumFormula = split[3].trim();
                score = Double.parseDouble(split[4].substring(split[4].indexOf(".")));
                // 	number of matched fragment peaks from MassBank
                int pos1 = split[4].indexOf(".");
                if ( pos1 > 0 ) { 
					numHits = Integer.parseInt(split[4].substring(0, pos1).trim());
				}
				else {
					numHits = Integer.parseInt(split[4]);
				}
                site = split[5];

                String url = getServerUrl();
                if(!url.contains("Dispatcher.jsp") && url.endsWith("/"))
                	url += "jsp/Dispatcher.jsp?type=disp&id=" + id + "&site=" + site + "&qmz=" + getQmz() + "&CUTOFF=5";
                
                //String record = MassBankUtilities.retrieveRecord(id, site);
                mbu.fetchRecord(id, site);
                //String mol = MassBankUtilities.retrieveMol(name, site, id);

//                String prefix = id.substring(0, 2);
                String prefix = "";
        		if(id.matches("[A-Z]{3}[0-9]{5}"))
        			prefix = id.substring(0, 3);
        		else prefix = id.substring(0, 2);
                cache = new File(dir, prefix);
        		
                String basePath = "";
                boolean createDir = false;
                if(!cache.exists()) {		// cache folder does not exist, create it
                	createDir = cache.mkdirs();
                	if(createDir) {
                		System.out.println("created directory [" + cache.getAbsolutePath() + "] -> " + createDir);
                		File molDir = new File(cache, MOL);
                        File recDir = new File(cache, RECORDS);
                        System.out.println("created molDir ? " + molDir.mkdir() + "\treated recDir ? " + recDir.mkdir());	// create subdirectories
                        basePath = molDir.getAbsolutePath();
                	}
                }
                else {		// cache folder already exists 
                	File molDir = new File(cache, fileSeparator + MOL + fileSeparator);
                	if(!molDir.isDirectory())
                		molDir.mkdirs();
                	
                	basePath = molDir.getAbsolutePath();
                }
//                String prefix = "";
//        		if(id.matches("[A-Z]{3}[0-9]{5}"))
//        			prefix = id.substring(0, 3);
//        		else prefix = id.substring(0, 2);
//                //File dir = new File(cacheMassBank);
//                String[] institutes = dir.list();
//                File f = null;
//                String basePath = "";
//                if(institutes != null & institutes.length > 0) {
//                	for (int j = 0; j < institutes.length; j++) {
//                        if(institutes[j].equals(prefix)) {
//                            f = new File(dir, institutes[j] + "/mol/");
//                            basePath = f.getAbsolutePath();
//                            if(!basePath.endsWith("/"))
//                                    basePath += "/";
//                            break;
//                        }
//                    }
//                }
                
                //boolean fetch = MassBankUtilities.fetchMol(name, id, site, basePath);
                boolean fetch = false;
                //boolean write = MassBankUtilities.writeMolFile(id, mol, basePath);

                // create AtomContainer via SMILES
                Map<String, String> links = mbu.retrieveLinks(id, site);
                String smiles = links.get("smiles");
                String inchi = links.get("inchi");	// IUPAC InChI from MassBank record
                //System.out.println("smiles -> " + smiles);
                IAtomContainer container = null;
                /** TODO: order seems to matter! mol container via MassBank is different from InChI generated one !*/
                // first look if container is present, then download if not
                container = mbu.getContainer(id, basePath);
                
                if(container == null) {
                    fetch = mbu.fetchMol(name, id, site, basePath);
                    if(fetch) {
                        System.out.println("container via fetch");
                        //container = MassBankUtilities.getMolFromAny(id, basePath, smiles);
                        container = mbu.getContainer(id, basePath);
                    }
                    else {
                        System.out.println("container via smiles");
                        container = mbu.getMolFromSmiles(smiles);

                        if(container != null) {
                            // write out molfile
                            File mol = new File(basePath, id + ".mol");
                            mbu.writeContainer(mol, container);
                        }
                    }
                }

                /** TODO: order seems to matter! mol container via MassBank is different from InChI generated one !*/
                if(isUniqueInchi() && container == null && inchi != null && !inchi.isEmpty()) {	// check if InChI string is present
	                try {	// create container via InChI
						container = igf.getInChIToStructure(inchi, DefaultChemObjectBuilder.getInstance()).getAtomContainer();
					} catch (CDKException e) {
						container = null;
					}
                }
                
                boolean isConnected = false;
                if(container != null)
                	isConnected = ConnectivityChecker.isConnected(container);
                /**
                 *  if entry is not present yet, add it - else don't
                 */
                if(container != null && isConnected) {	// removed duplicate check -> !duplicates.contains(name) &&
                	
                	// compute molecular formula
					IMolecularFormula iformula = MolecularFormulaManipulator.getMolecularFormula(container);
					if(iformula == null)	// fallback to MassBank sum formula
						iformula = MolecularFormulaManipulator.getMolecularFormula(sumFormula, DefaultChemObjectBuilder.getInstance());
					String formula = MolecularFormulaManipulator.getHTML(iformula);
					// compute molecular mass
					double emass = 0.0d;
					if(!formula.contains("R"))	// compute exact mass from formula only if NO residues "R" are present
						emass = MolecularFormulaManipulator.getTotalExactMass(iformula);
					else emass = mbu.retrieveExactMass(id, site);
					
                    duplicates.add(name);
                    //results.add(new Result("MassBank", id, name, score, container, url, relImagePath + id + ".png"));
                    String imgPath = tempPath + id + ".png";
                    Result r = new Result(databaseName, id, name, score, container, url, imgPath, formula, emass);
                    r.setMatchingPeaks(numHits);		// set number of matched peaks
                    //results.add(r);
                    //limitCounter++;
                    
                    /**
                     * enable code and include de.ipbhalle.metfusion.utilities.chemaxon.ChemAxonUtilities in build path to access
                     * ChemAxon provided functionality. [3/3]
                    // generate ECFP
                    if(useChemAxon) {
	                    File f1 = new File(basePath, id + ".mol");	// path to mol file
	                    ECFP ecfp = cau.generateECFPFromMol(f1);		// generate ECFP from mol file
	                    r.setBitset(ecfp.toBitSet());				// set BitSet from ECFP
	                    r.setEcfp(ecfp);							// store ECFP in result
                    }
                    */
                    
                    if(isUniqueInchi()) {		// if filter for unique InChI is on
	                    String inchikey = r.getInchikey().split("-")[0];
	                    if(inchi == null || inchi.isEmpty() || inchikey == null || inchikey.isEmpty()) {
		                    try {
		                    	InChIGenerator ig = igf.getInChIGenerator(container);
		                    	if(ig.getReturnStatus() == INCHI_RET.ERROR) {
		                    		inchi = "";
		                    		inchikey = "";
		                    	}
		                    	else {
		                    		inchi = ig.getInchi();
									inchikey = ig.getInchiKey().split("-")[0];
		                    	}
							} catch (CDKException e) {
								inchi = "";
								inchikey = "";
							}
	                    }
	                    
	                    if(inchikey.isEmpty()) {	// add record if no InChI-key present
	                    	results.add(r);			// add result
	                    	limitCounter++;			// increase limit counter
	                    }
	                    else if(!inchiMap.containsKey(inchikey)) {		
	                    	inchiMap.put(inchikey, imgPath);		// store InChI-Key with image path
	                    	results.add(r);							// add result
	                    	limitCounter++;							// increase limit counter
	                    }
	                    else {			// InChI-Key already present in map -> skip entry
	                    	r.setImagePath(inchiMap.get(inchikey));	// use original structure image for duplicate
	                    	unused.add(r);				// add result to unused list
	                    }
                    }
                    else {		// if filter for unique InChI is off, stick to normal behaviour and add all results
                    	results.add(r);
                    	limitCounter++;
                    }
                }

                // add unused results (duplicate or no mol container) to list
                if((!fetch && container == null) | !isConnected) {
                	Result r = new Result(databaseName, id, name, score, container, url, tempPath + id + ".png");
                	double emass = Double.parseDouble(links.get("emass"));
                	r.setExactMass(emass);
                	r.setSumFormula(links.get("formula"));
                	r.setMatchingPeaks(numHits);		// set number of matched peaks
                	unused.add(r);
                	System.out.println("unused -> " + id);
                }
            }
            else if(split.length == 7) {
                    System.err.println("length == 7");
            }
            else {
            	System.err.println("unknown split length! - time to update MassBank format!?!");
            }
        }
        // ensure progress bar set to 100% - can be lower if not all results had moldata, thus not increasing limitCounter
        this.searchCounter = limit; 	//results.size();	//resultLimit;
        //this.limit = results.size();
        updateSearchProgress();	// update progress bar
        
        System.out.println("entries after duplicate removal -> " + results.size());
        this.results = results;
        if(unused.size() > 0) {	// entries are left out because of missing structure information
        	System.out.println(missingEntriesNote);
        	setShowNote(Boolean.TRUE);
        }
        else setShowNote(Boolean.FALSE);
	}
	
	private String formatPeaks() {
		StringBuilder peaklist =  new StringBuilder();
		String temp = inputSpectrum.trim();
		StringBuilder qmz = new StringBuilder();	// builder for mz values only
		
		// assume that validation took place -> one peak per line, mz space int
		String[] split = temp.split("\n");
		for (int i = 0; i < split.length; i++) {
			String[] line = split[i].split("\\s");
			
			String mz = "";
			String inte = "";
			
			if(line.length == 0) {
				System.err.println("Error parsing peaklist!");
				continue;
			}
			else if(line.length == 1) {
				// assume that only mz values are given
				mz = line[0];
				inte = "100";
			}
			else if(line.length == 2) {
				mz = line[0];
				inte = line[1];
			}
			else if(line.length == 3) {
				// assume that first mz, then rel.int, then int
				mz = line[0];
				inte = line[2];
			}
			else {
				// assume that first mz, then rel.int, then int
				mz = line[0];
				inte = line[2];
			}
			
			if(i == (split.length - 1))
				peaklist.append(mz).append(",").append(inte);	
			else
				peaklist.append(mz).append(",").append(inte).append("@");
			
			qmz.append(mz).append(",");
		}
		
		// set qmz field
		setQmz(qmz.toString());
		
		return peaklist.toString();
	}
	
	public void reset(ActionEvent event) {
		this.showResult = false;
		this.queryResults = new ArrayList<String>();
		
		FacesContext fc = FacesContext.getCurrentInstance();
        ELResolver el = fc.getApplication().getELResolver();
        ELContext elc = fc.getELContext();
        MetFragBean mfb = (MetFragBean) el.getValue(elc, null, "metFragBean");
        mfb.reset(null);
	}
	
	public String getServerUrl() {
		return serverUrl;
	}


	public Map<String, List<String>> getInstruments() {
		return instruments;
	}


	public void setServerUrl(String serverUrl) {
		this.serverUrl = serverUrl;
		setupMassBank(serverUrl);		// after new serverUrl been set, setup MassBank
	}


	public void setInstruments(Map<String, List<String>> instruments) {
		this.instruments = instruments;
	}


	public void setMbCommon(MassBankCommon mbCommon) {
		this.mbCommon = mbCommon;
	}


	public MassBankCommon getMbCommon() {
		return mbCommon;
	}


	public void setConfig(GetConfig config) {
		this.config = config;
	}


	public GetConfig getConfig() {
		return config;
	}


	public GetInstInfo getInstInfo() {
		return instInfo;
	}


	public void setInstInfo(GetInstInfo instInfo) {
		this.instInfo = instInfo;
	}

	public void setInputSpectrum(String inputSpectrum) {
		this.inputSpectrum = inputSpectrum.trim();
	}

	public String getInputSpectrum() {
		return inputSpectrum.trim();
	}

	public void setInsts(SelectItem[] insts) {
		this.insts = insts;
	}

	public SelectItem[] getInsts() {
		return insts;
	}

	public void setSelectedInstruments(String[] selectedInstruments) {
		this.selectedInstruments = selectedInstruments;
	}

	public String[] getSelectedInstruments() {
		return selectedInstruments;
	}

	public void setGroupInstruments(List<SelectItemGroup> groupInstruments) {
		this.groupInstruments = groupInstruments;
	}

	public List<SelectItemGroup> getGroupInstruments() {
		return groupInstruments;
	}

	public void setIonisations(SelectItem[] ionisations) {
		this.ionisations = ionisations;
	}

	public SelectItem[] getIonisations() {
		return ionisations;
	}

	public void setSelectedIon(String selectedIon) {
		this.selectedIon = selectedIon;
	}

	public String getSelectedIon() {
		return selectedIon;
	}

	public List<String> getQueryResults() {
		return queryResults;
	}

	public void setQueryResults(List<String> queryResults) {
		this.queryResults = queryResults;
	}

	public void setShowResult(boolean showResult) {
		this.showResult = showResult;
	}

	public boolean isShowResult() {
		return showResult;
	}

	public void setResults(List<Result> results) {
		this.results = results;
	}

	public List<Result> getResults() {
		return results;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public int getLimit() {
		return limit;
	}

	public void setOriginalResults(List<String> originalResults) {
		this.originalResults = originalResults;
	}

	public List<String> getOriginalResults() {
		return originalResults;
	}

	public void setUnused(List<Result> unused) {
		this.unused = unused;
	}

	public List<Result> getUnused() {
		return unused;
	}

	public void setQmz(String qmz) {
		this.qmz = qmz;
	}

	public String getQmz() {
		return qmz;
	}

	public void setSessionPath(String sessionPath) {
		this.sessionPath = sessionPath;
	}

	public String getSessionPath() {
		return sessionPath;
	}

	public boolean isUseEIOnly() {
		return useEIOnly;
	}

	public void setUseEIOnly(boolean useEIOnly) {
		this.useEIOnly = useEIOnly;
	}

	public boolean isUseESIOnly() {
		return useESIOnly;
	}

	public void setUseESIOnly(boolean useESIOnly) {
		this.useESIOnly = useESIOnly;
	}

	public boolean isUseOtherOnly() {
		return useOtherOnly;
	}

	public void setUseOtherOnly(boolean useOtherOnly) {
		this.useOtherOnly = useOtherOnly;
	}

	public boolean isUseLC() {
		return useLC;
	}

	public void setUseLC(boolean useLC) {
		this.useLC = useLC;
	}

	public boolean isUseGC() {
		return useGC;
	}

	public void setUseGC(boolean useGC) {
		this.useGC = useGC;
	}

	public void setInstGroups(Map<String, List<String>> instGroups) {
		this.instGroups = instGroups;
	}

	public Map<String, List<String>> getInstGroups() {
		return instGroups;
	}

	public void setCurrentRecord(String currentRecord) {
		this.currentRecord = currentRecord;
	}

	public String getCurrentRecord() {
		return currentRecord;
	}

	public void setCutoff(int cutoff) {
		this.cutoff = cutoff;
	}

	public int getCutoff() {
		return cutoff;
	}

	public void setBrokenMassBank(boolean brokenMassBank) {
		this.brokenMassBank = brokenMassBank;
	}

	public boolean isBrokenMassBank() {
		return brokenMassBank;
	}

	public void setInstTest(List<SelectItem[]> instTest) {
		this.instTest = instTest;
	}

	public List<SelectItem[]> getInstTest() {
		return instTest;
	}

	public void setSelectedGroupInstruments(List<String[]> selectedGroupInstruments) {
		this.selectedGroupInstruments = selectedGroupInstruments;
	}

	public List<String[]> getSelectedGroupInstruments() {
		return selectedGroupInstruments;
	}

	public static String getEi() {
		return EI;
	}

	public static String getEsi() {
		return ESI;
	}

	public static String getOther() {
		return OTHER;
	}

	public static String getSessionmapkeyinstruments() {
		return SESSIONMAPKEYINSTRUMENTS;
	}

	public void setLinkGroupEI(String linkGroupEI) {
		this.linkGroupEI = linkGroupEI;
	}

	public String getLinkGroupEI() {
		return linkGroupEI;
	}

	public void setLinkGroupESI(String linkGroupESI) {
		this.linkGroupESI = linkGroupESI;
	}

	public String getLinkGroupESI() {
		return linkGroupESI;
	}

	public void setLinkGroupOTHER(String linkGroupOTHER) {
		this.linkGroupOTHER = linkGroupOTHER;
	}

	public String getLinkGroupOTHER() {
		return linkGroupOTHER;
	}

	public void setInstrumentToGroup(Map<String, String> instrumentToGroup) {
		this.instrumentToGroup = instrumentToGroup;
	}

	public Map<String, String> getInstrumentToGroup() {
		return instrumentToGroup;
	}

	public void setSearchProgress(int searchProgress) {
		this.searchProgress = searchProgress;
	}

	public int getSearchProgress() {
		return searchProgress;
	}

	public void setSearchCounter(int searchCounter) {
		this.searchCounter = searchCounter;
	}

	public int getSearchCounter() {
		return searchCounter;
	}

	public void setRunning(boolean isRunning) {
		this.isRunning = isRunning;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public void setDone(boolean done) {
		this.done = done;
	}

	public boolean isDone() {
		return done;
	}

	public boolean isPresentEI() {
		return presentEI;
	}

	public void setPresentEI(boolean presentEI) {
		this.presentEI = presentEI;
	}

	public boolean isPresentESI() {
		return presentESI;
	}

	public void setPresentESI(boolean presentESI) {
		this.presentESI = presentESI;
	}

	public boolean isPresentOther() {
		return presentOther;
	}

	public void setPresentOther(boolean presentOther) {
		this.presentOther = presentOther;
	}

	public void setMissingEntriesNote(String missingEntriesNote) {
		this.missingEntriesNote = missingEntriesNote;
	}

	public String getMissingEntriesNote() {
		return missingEntriesNote;
	}

	public void setShowNote(boolean showNote) {
		this.showNote = showNote;
	}

	public boolean isShowNote() {
		return showNote;
	}

	public void setUniqueInchi(boolean uniqueInchi) {
		this.uniqueInchi = uniqueInchi;
	}

	public boolean isUniqueInchi() {
		return uniqueInchi;
	}

	public void setSelectOtherServer(boolean selectOtherServer) {
		this.selectOtherServer = selectOtherServer;
	}

	public boolean isSelectOtherServer() {
		return selectOtherServer;
	}

	public boolean isAllowOtherServer() {
		return allowOtherServer;
	}

	public void setAllowOtherServer(boolean allowOtherServer) {
		this.allowOtherServer = allowOtherServer;
	}

	public void setOtherServer(String otherServer) {
		this.otherServer = otherServer;
	}

	public String getOtherServer() {
		return otherServer;
	}

	public int getNUM_INST_GROUPS() {
		return NUM_INST_GROUPS;
	}

	public void setNUM_INST_GROUPS(int nUM_INST_GROUPS) {
		NUM_INST_GROUPS = nUM_INST_GROUPS;
	}

	public int getIndexEI() {
		return indexEI;
	}

	public void setIndexEI(int indexEI) {
		this.indexEI = indexEI;
	}

	public int getIndexESI() {
		return indexESI;
	}

	public void setIndexESI(int indexESI) {
		this.indexESI = indexESI;
	}

	public int getIndexOther() {
		return indexOther;
	}

	public void setIndexOther(int indexOther) {
		this.indexOther = indexOther;
	}

	public void setAvailableServers(SelectItem[] availableServers) {
		this.availableServers = availableServers;
	}

	public SelectItem[] getAvailableServers() {
		return availableServers;
	}

	public void setSelectedServer(String selectedServer) {
		this.selectedServer = selectedServer;
	}

	public String getSelectedServer() {
		return selectedServer;
	}

	public void setPreviousServer(String previousServer) {
		this.previousServer = previousServer;
	}

	public String getPreviousServer() {
		return previousServer;
	}

	@Override
	public void setDatabaseName(String name) {
		databaseName = name;
	}

	@Override
	public String getDatabaseName() {
		return databaseName;
	}

	public SelectItem[] getAvailableMSLevels() {
		return availableMSLevels;
	}

	public void setAvailableMSLevels(SelectItem[] availableMSLevels) {
		this.availableMSLevels = availableMSLevels;
	}

	public String[] getSelectedMSLevel() {
		return selectedMSLevel;
	}

	public void setSelectedMSLevel(String[] selectedMSLevel) {
		this.selectedMSLevel = selectedMSLevel;
	}

}
