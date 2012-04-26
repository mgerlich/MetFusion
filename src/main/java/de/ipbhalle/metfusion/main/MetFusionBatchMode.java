/**
 * created by Michael Gerlich, Nov 8, 2010 - 12:47:12 PM
 * 
 * Utility class providing a batch mode for console to MetFusion functionality.
 */ 

package de.ipbhalle.metfusion.main;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openscience.cdk.interfaces.IAtomContainer;

import de.ipbhalle.enumerations.Adducts;
import de.ipbhalle.enumerations.Databases;
import de.ipbhalle.enumerations.Ionizations;
import de.ipbhalle.enumerations.OutputFormats;
import de.ipbhalle.metfusion.threading.MetFusionThreadBatchMode;
import de.ipbhalle.metfusion.utilities.MassBank.MassBankUtilities;
import de.ipbhalle.metfusion.web.controller.ResultExtGroupBean;
import de.ipbhalle.metfusion.wrapper.ColorcodedMatrix;
import de.ipbhalle.metfusion.wrapper.ResultExt;


public class MetFusionBatchMode {

	private final static String ARGUMENT_INDICATOR = "-";
	// batchfile, sdf-file
	public static enum ARGUMENTS {mf, sdf, out, format, proxy, record};
	private final static int NUM_ARGS = ARGUMENTS.values().length;
	private boolean checkMF, checkSDF, checkOUT, checkFORMAT, checkPROXY, checkRECORD;
	private Map<ARGUMENTS, String> settings;
	
	private final String os = System.getProperty("os.name");
	private final String fileSeparator = System.getProperty("file.separator");
	private final String userHome = System.getProperty("user.home");
	private final String currentDir = System.getProperty("user.dir");
	private final String tempDir = System.getProperty("java.io.tmpdir");
	private final String lineSeparator = System.getProperty("line.separator");
	private final String fileEncoding = System.getProperty("file.encoding");
	
	private boolean doneCheck = false;
	private boolean doneSetup = false;
	
	private MetFusionBatchFileHandler batchFileHandler;
	private ColorcodedMatrix colorMatrix;
	private ColorcodedMatrix colorMatrixAfter;
	private List<ResultExt> secondOrder;
	private List<ResultExt> clusterResults;
	private List<ResultExtGroupBean> tanimotoClusters;
	
	
	public MetFusionBatchMode(String[] args) {
		this.doneSetup = setup();
		this.doneCheck = checkArguments(args);
	}
	
	/**
	 * Setup the current path environment to write result files.
	 * 
	 * @return <p><b>true</b> if the current directory where this class is being run is a directory
	 * and it is read-/writable.
	 * <p><b>false</b> if the current directory or path is not useable. Fallback to tmp dir.
	 */
	private boolean setup() {
		boolean success = false;
		File dir = new File(currentDir);
		if(dir.isDirectory() && dir.canRead() && dir.canWrite())
			success = true;
		return success;
	}
	
	private boolean checkArguments(String[] args) {
		boolean success = false;
		OutputFormats[] of = OutputFormats.values();
		
		if(args.length < 4) {	// at least -mf and -sdf OR -out needs to be specified
			System.out.println("Please provide the following arguments:");
			System.out.println("-mf /path/to/mf-file");
			System.out.println("Alternatively: -record /path/to/MassBank-record");
			System.out.println("optionally: -sdf /path/to/sdf-file");
			System.out.println("-out /output/path");
			System.out.print("-format ");
			for (OutputFormats format : of) {
				System.out.print("[" + format + "] ");
			}
			System.out.println("optionally: -proxy");
			
			System.out.println("\nExample call: java -jar JARFILE -mf settings.mf #this uses the current directory for output!");
			System.out.println("Example call: java -jar JARFILE -mf settings.mf -out /tmp");
			System.out.println("Example call: java -jar JARFILE -mf settings.mf -sdf compounds.sdf -out /tmp");
			System.out.println("Example call: java -jar JARFILE -record XX000001.txt -out /tmp -format SDF");
			System.out.println("Example call: java -jar JARFILE -mf settings.mf -sdf compounds.sdf -out /tmp -format SDF");
			System.out.println("Example call: java -jar JARFILE -mf settings.mf -sdf compounds.sdf -out /tmp -format SDF -proxy");
			
			return success;
		}
		
		this.settings = new HashMap<ARGUMENTS, String>();
		for (int i = 0; i < args.length; i++) {
			if(args[i].startsWith(ARGUMENT_INDICATOR)) {
				String temp = args[i].substring(1);	// skip indicator char
				if(temp.equals(ARGUMENTS.mf.toString()))
					this.checkMF = Boolean.TRUE;
				if(temp.equals(ARGUMENTS.sdf.toString())) {
					this.checkSDF = Boolean.TRUE;
					System.out.println("SDF provided!");
				}
				if(temp.equals(ARGUMENTS.out.toString()))
					this.checkOUT = Boolean.TRUE;
				if(temp.equals(ARGUMENTS.format.toString()))
					this.checkFORMAT = Boolean.TRUE;
				if(temp.equals(ARGUMENTS.proxy.toString())) {
					this.checkPROXY = Boolean.TRUE;	// proxy does not have an additional property, mark as set/unset and continue
					continue;
				}
				if(temp.equals(ARGUMENTS.record.toString())) {
					this.checkRECORD = Boolean.TRUE;
				}
				
				settings.put(ARGUMENTS.valueOf(temp), args[i+1]);	// put value into map
				i++;	// skip value, iterate over new argument
			}
		}
		if(!checkOUT) {
			settings.put(ARGUMENTS.out, currentDir);
			checkOUT = Boolean.TRUE;
		}
		if(checkMF & checkSDF | checkMF & checkOUT | checkRECORD & checkOUT)
			success = true;
		
		return success;
	}
	
	/**
	 * Provide arguments in the following way:
	 * 
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		MetFusionBatchMode mfbm = new MetFusionBatchMode(args);
		
		if(mfbm.doneSetup)
			System.out.println("Setup valid!");
		else {
			System.err.println("Setup failed!");
			System.exit(-1);
		}
		
		if(mfbm.doneCheck)
			System.out.println("Check arguments done - valid!");
		else {
			System.err.println("Error checking arguments - invalid!");
			System.exit(-1);
		}
		
		// decide whether or not to use SDF -> use SDF if -sdf was set
		// if not, use defined database setting in -mf file
//		String mfFile = mfbm.settings.get(ARGUMENTS.mf);
//		File mfFileHandler = new File(mfFile);
//		String prefix = mfFileHandler.getName().substring(0, mfFileHandler.getName().lastIndexOf("."));	// name of batch file - use as prefix for output files!
		
		String prefix = "";
		if(mfbm.checkMF) {
			String mfFile = mfbm.settings.get(ARGUMENTS.mf);
			File mfFileHandler = new File(mfFile);
			prefix = mfFileHandler.getName().substring(0, mfFileHandler.getName().lastIndexOf("."));	// name of batch file - use as prefix for output files!
			mfbm.batchFileHandler = new MetFusionBatchFileHandler(mfFileHandler);
			try {
				mfbm.batchFileHandler.readFile();
			} catch (IOException e) {
				System.err.println("Error while reading settings file [" + mfFile + "]");
				System.exit(-1);
			}
			mfbm.batchFileHandler.printSettings();
		}
		
		// set parameters for fragmenter and database threads
		String outPath = mfbm.settings.get(ARGUMENTS.out);
		if(!outPath.endsWith(mfbm.fileSeparator))
			outPath += mfbm.fileSeparator;
		
		MetFusionBatchSettings settings = null;
		if(mfbm.checkMF)
			settings = mfbm.batchFileHandler.getBatchSettings();
		else if(mfbm.checkRECORD)
			settings = new MetFusionBatchSettings();		// use default settings
		
		Ionizations ion = settings.getMbIonization();		// retrieve ionization for MassBank
		
		MetFragBatchMode metfragbm = new MetFragBatchMode(outPath);
		MassBankBatchMode mbbm = new MassBankBatchMode(outPath, ion);
		
		
		mbbm.setInputSpectrum(settings.getPeaks());
		mbbm.setSelectedInstruments(settings.getMbInstruments());
		mbbm.setLimit(settings.getMbLimit());
		
		metfragbm.setInputSpectrum(settings.getPeaks());
		metfragbm.setSelectedDB(settings.getMfDatabase().toString());
		metfragbm.setMolecularFormula(settings.getMfFormula());
		metfragbm.setSelectedAdduct(settings.getMfAdduct().getDifference());
		metfragbm.setParentIon(settings.getMfParentIon());
		metfragbm.setExactMass(settings.getMfExactMass());
		metfragbm.setMzabs(settings.getMfMZabs());
		metfragbm.setMzppm(settings.getMfMZppm());
		metfragbm.setSearchppm(settings.getMfSearchPPM());
		metfragbm.setLimit(settings.getMfLimit());
		metfragbm.setDatabaseID(settings.getMfDatabaseIDs());
		
		if(mfbm.checkRECORD) {			// overwrite default settings with record specific ones
			File f = new File(mfbm.settings.get(ARGUMENTS.record));
			prefix = f.getName().substring(0, f.getName().lastIndexOf("."));	// name of batch file - use as prefix for output files!
			MassBankUtilities mbu = new MassBankUtilities();
			//String[] result = MassBankUtilities.getPeaklistFromFile(f);		// read in record
			String[] result = mbu.getPeaklistFromFile(f);		// read in record
			
			mbbm.setInputSpectrum(result[0]);		// set peaks for MassBank
			metfragbm.setInputSpectrum(result[0]);	// set peaks for MetFrag
			
			metfragbm.setExactMass(Double.valueOf(result[1]));	// set exact mass
			metfragbm.setParentIon(Double.valueOf(result[1]));	// set parent ion same as exact mass
			metfragbm.setSelectedAdduct(Adducts.Neutral.getDifference());	// default not neutral adduct
			
			ion = Ionizations.valueOf(result[3]);				// set correct ionization from record
			
			metfragbm.setSelectedDB(Databases.pubchem.toString());	// default to Pubchem database
		}
		
		if(mfbm.checkPROXY)		// if proxy switch was set, use proxy in MetFrag
			metfragbm.setProxy(Boolean.TRUE);
		
		// sdf path
		if(mfbm.isCheckSDF()) {
			String sdfFile = mfbm.settings.get(ARGUMENTS.sdf);
			File sdf = new File(sdfFile);
			List<IAtomContainer> compounds = mfbm.batchFileHandler.consumeSDF(sdf.getAbsolutePath());
			System.out.println("#compounds from sdf file -> " + compounds.size());
			metfragbm.setSelectedSDF(sdf.getAbsolutePath());
		}
		
		OutputFormats of = OutputFormats.SDF;	// DEFAULT output format
		if(mfbm.checkFORMAT)
			of = OutputFormats.valueOf(mfbm.settings.get(ARGUMENTS.format));
		
		MetFusionThreadBatchMode metfusionBatch = new MetFusionThreadBatchMode(mfbm, mbbm, metfragbm, outPath, prefix, of);
		metfusionBatch.run();
	}

	public void setColorMatrix(ColorcodedMatrix colorMatrix) {
		this.colorMatrix = colorMatrix;
	}

	public ColorcodedMatrix getColorMatrix() {
		return colorMatrix;
	}

	public void setColorMatrixAfter(ColorcodedMatrix colorMatrixAfter) {
		this.colorMatrixAfter = colorMatrixAfter;
	}

	public ColorcodedMatrix getColorMatrixAfter() {
		return colorMatrixAfter;
	}

	public void setSecondOrder(List<ResultExt> secondOrder) {
		this.secondOrder = secondOrder;
	}

	public List<ResultExt> getSecondOrder() {
		return secondOrder;
	}

	public void setTanimotoClusters(List<ResultExtGroupBean> tanimotoClusters) {
		this.tanimotoClusters = tanimotoClusters;
	}

	public List<ResultExtGroupBean> getTanimotoClusters() {
		return tanimotoClusters;
	}

	public boolean isCheckMF() {
		return checkMF;
	}

	public void setCheckMF(boolean checkMF) {
		this.checkMF = checkMF;
	}

	public boolean isCheckSDF() {
		return checkSDF;
	}

	public void setCheckSDF(boolean checkSDF) {
		this.checkSDF = checkSDF;
	}

	public boolean isCheckOUT() {
		return checkOUT;
	}

	public void setCheckOUT(boolean checkOUT) {
		this.checkOUT = checkOUT;
	}

	public List<ResultExt> getClusterResults() {
		return clusterResults;
	}

	public void setClusterResults(List<ResultExt> clusterResults) {
		this.clusterResults = clusterResults;
	}

}
