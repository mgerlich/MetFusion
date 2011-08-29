/** 
 * created 19.11.2009 - 13:39:50 by Michael Gerlich
 * email: mgerlich@ipb-halle.de
 */

package de.ipbhalle.metfusion.utilities.MassBank;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import massbank.GetConfig;
import massbank.MassBankCommon;
import net.sf.jniinchi.INCHI_RET;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.openscience.cdk.ChemFile;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.inchi.InChIGeneratorFactory;
import org.openscience.cdk.inchi.InChIToStructure;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IChemFile;
import org.openscience.cdk.interfaces.IMolecule;
import org.openscience.cdk.io.MDLReader;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.io.MDLV2000Writer;
import org.openscience.cdk.io.SDFWriter;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.ChemFileManipulator;

import de.ipbhalle.CDK.ErrorHandler.ErrorHandler;
import java.util.List;

/**
 * The Class MassBankUtilities.
 */
public class MassBankUtilities {

	/** The Constant baseUrl. */
	private final static String baseUrl = "http://msbi.ipb-halle.de/MassBank/";	
	//"http://www.massbank.jp/";	//  http://msbi.ipb-halle.de/MassBank/
	
	/** The server url. */
	private static String serverUrl;
	
	/** The Constant cacheMassBank. */
	private static final String cacheMassBank = "/vol/massbank/Cache/";
	
	/**
	 * Format a peaklist string into the appropriate format used for
	 * MassBank web queries.
	 * 
	 * @param peaks the String to be converted and which contains a proper peaklist consisting
	 * of pairwise m/z and intensity values.
	 * @return a formatted String, here all spaces have been converted to commas, and all
	 * line breaks have been converted into @
	 */
	public static String formatPeaksForMassBank(String peaks) {
		peaks = peaks.replaceAll(" ", ",");
		peaks = peaks.replaceAll("\n", "@");
		return peaks;
	}
	
	/**
	 * A utitility function to parse a MassBank file and store the corresponding
	 * peaklist, exact mass and compound name/id for further use.
	 * 
	 * @param f - the file containing the MassBank record
	 * @return an array of Strings, [0] contains the peaklist, [1] stores the exact mass
	 * and [2] contains the compound name or identifier
	 */
	public static String[] getPeaklistFromFile(File f) {
		StringBuilder sb = new StringBuilder();
		String[] data = new String[3];
		String mass = "";
		String compound = "";
		boolean gotName = false;
		try {
			BufferedReader br = new BufferedReader(new FileReader(f));
			String line = "";
			int counter = 0;
			while((line = br.readLine()) != null) {
				/**
				 * all spectra
				 */
				if(line.startsWith("ACCESSION:") && !gotName) {
					compound = line.substring(line.indexOf(":") + 1).trim();
					gotName = true;
				}
				
				/**
				 * RIKEN spectra
				 */
//				if(line.startsWith("ACCESSION: PR") && !gotName) {
//					compound = line.substring(line.indexOf(":") + 1).trim();
//					gotName = true;
//				}
				
				/**
				 * QSTAR spectra
				 */
//				if(line.startsWith("ACCESSION: PB") && !gotName) {
//					compound = line.substring(line.indexOf(":") + 1).trim();
//					gotName = true;
//				}
				
				/**
				 * Hill spectra
				 */
//				if(line.startsWith("CH$NAME:") && !gotName) {
//					// if 102 unique merged spectra are used
//					compound = line.substring(line.indexOf(":") + 1).trim();
//					
//					// if 510 single spectra are used with 5 spectra per compound
//					//compound = f.getName().substring(0, f.getName().indexOf("."));
//					gotName = true;
//				}
				
				if(line.startsWith("CH$EXACT_MASS:")) {
					mass = line.substring(line.indexOf(":") + 1).trim();
				}
				
				if(line.startsWith("PK$NUM_PEAK:")) {
					counter = Integer.parseInt(line.substring(line.indexOf(":") + 1).trim());
					// skip peak header line
					br.readLine();
					
					for (int i = 0; i < counter; i++) {
						String temp = br.readLine().trim();
						String[] split = temp.split(" ");
						if(split.length == 3 && i < (counter-1)) {
							sb.append(split[0]).append(" ").append(split[2]).append("\n");
						}
						else sb.append(split[0]).append(" ").append(split[2]);
					}
				}
			}
			br.close();
		} catch (FileNotFoundException e) {
			System.err.println("File not found -> " + f.getAbsolutePath());
			return null;
		} catch (IOException e) {
			System.err.println("Error parsing file -> " + f.getAbsolutePath());
			return null;
		}
		String result = sb.toString();
		if(result.endsWith("\n")) {
			result = result.substring(0, result.length() - 1);
		}
		
		data[0] = result;
		data[1] = mass;
		data[2] = compound;
		
		return data;
	}
	
	/**
	 * Write mol file.
	 * 
	 * @param id the MassBank id
	 * @param mol the mol string
	 * @param basePath the base path
	 * 
	 * @return true, if successful
	 */
	public static boolean writeMolFile(String id, String mol, String basePath) {
		boolean success = false;
		File f = new File(basePath + id + ".mol");
//		if(f.exists()){
//			System.out.println("File " + f +  " exists!");
//			return true;
//		}
		
		/**
		 *  check if mol String matches to standard
		 */
		// mol string contains 2 additional lines required for MassBank molview applet - remove for standard conformity
		Pattern p = Pattern.compile("[0-9]+\n[0-9]+\n.*\n.*\n\\s[0-9]+.*V2000");
		if(p.matcher(mol).lookingAt()) {	// if pattern is found
			Pattern p1 = Pattern.compile("[0-9]+\n[0-9]+\n");
			mol = p1.matcher(mol).replaceFirst("\n");	// remove first two lines by single empty line --> 3 line header block
			System.out.println("non-standard MassBank mol file - removed unused lines for conformity!");
		}

		// mol string contains only 2 header rows - add additional blank line for standard conforming 3 line header
		p = Pattern.compile(".*\n.*\n\\s[0-9]+.*V2000");
		if(p.matcher(mol).lookingAt()) {	// if pattern is found
			Pattern p1 = Pattern.compile(".*\n.*\n");
			//mol = p1.matcher(mol).replaceFirst(p1.matcher(mol).group() + "\n");	// add blank line --> 3 line header block
			mol = p1.matcher(mol).replaceFirst("\n\n\n");	// add blank line --> 3 line header block
			System.out.println("non-standard MassBank mol file - removed unused lines for conformity!");
		}

		// mol string contains 5 header lines required for MassBank molview applet - remove first two for standard conformity
		p = Pattern.compile("[0-9]+\n[0-9]+\n.*\n.*\n.*\n\\s[0-9]+.*V2000");
		if(p.matcher(mol).lookingAt()) {	// if pattern is found
			Pattern p1 = Pattern.compile("[0-9]+\n[0-9]+\n");
			mol = p1.matcher(mol).replaceFirst("");	// remove first two lines by single empty line --> 3 line header block
			System.out.println("non-standard MassBank mol file - removed unused lines for conformity!");
		}
		
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(f));
			bw.write(mol);
			bw.flush();
			bw.close();
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
			success = false;
		}
		return success;
	}
	
	/**
	 * Write mol file.
	 * 
	 * @param id the MassBank id
	 * @param mol the mol string
	 * @param basePath the base path
	 * @param overwrite the overwrite
	 * 
	 * @return true, if successful
	 */
	public static boolean writeMolFile(String id, String mol, String basePath, boolean overwrite) {
		boolean success = false;
		File f = new File(basePath + id + ".mol");
		if(f.exists() && !overwrite){
			System.out.println("File " + f +  " exists!");
			return false;
		}
		
		/**
		 *  check if mol String matches to standard
		 */
		// mol string contains 2 additional lines required for MassBank molview applet - remove for standard conformity
		Pattern p = Pattern.compile("[0-9]+\n[0-9]+\n.*\n.*\n [0-9]+.*V2000");
		if(p.matcher(mol).lookingAt()) {	// if pattern is found
			Pattern p1 = Pattern.compile("[0-9]+\n[0-9]+\n");
			mol = p1.matcher(mol).replaceFirst("\n");	// remove first two lines by single empty line --> 3 line header block
			System.out.println("non-standard MassBank mol file - removed unused lines for conformity!");
		}

		// mol string contains only 2 header rows - add additional blank line for standard conforming 3 line header
		p = Pattern.compile(".*\n.*\n [0-9]+.*V2000");
		if(p.matcher(mol).lookingAt()) {	// if pattern is found
			Pattern p1 = Pattern.compile(".*\n.*\n");
			mol = p1.matcher(mol).replaceFirst(p1.matcher(mol).group() + "\n");	// remove first two lines by single empty line --> 3 line header block
			System.out.println("non-standard MassBank mol file - removed unused lines for conformity!");
		}

		// mol string contains 5 header lines required for MassBank molview applet - remove first two for standard conformity
		p = Pattern.compile("[0-9]+\n[0-9]+\n.*\n.*\n.*\n [0-9]+.*V2000");
		if(p.matcher(mol).lookingAt()) {	// if pattern is found
			Pattern p1 = Pattern.compile("[0-9]+\n[0-9]+\n");
			mol = p1.matcher(mol).replaceFirst("");	// remove first two lines by single empty line --> 3 line header block
			System.out.println("non-standard MassBank mol file - removed unused lines for conformity!");
		}
		
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(f));
			bw.write(mol);
			bw.flush();
			bw.close();
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
			success = false;
		}
		return success;
	}
	
	/**
	 * Fetch mol.
	 * 
	 * @param compound the compound
	 * @param id the id
	 * @param site the site
	 * @param basePath the base path
	 * 
	 * @return true, if successful
	 */
	public static boolean fetchMol(String compound, String id, String site, String basePath) {
		File f = new File(basePath + id + ".mol");
		if(f.exists()) {
			System.out.println(f + " exists");
			return true;
//			System.out.println("going to overwrite...");
		}

		String reqStr = baseUrl + "jsp/" + MassBankCommon.DISPATCHER_NAME;
		HttpClient client = new HttpClient();
		PostMethod method = new PostMethod(reqStr);
		method.addParameter("type", "mol");
		method.addParameter("qtype", "n");
		method.addParameter("otype", "m");
		method.addParameter("query", compound);
		method.addParameter("site", site);
		
		String mol = "";
		try {
			client.executeMethod(method);
			//mol = method.getResponseBodyAsString();
			
			// getResponseBodyAsStream()
			InputStream is = method.getResponseBodyAsStream();
			StringBuilder sb = new StringBuilder();
			String line = "";
			if (is != null) {
				try {
					BufferedReader reader = new BufferedReader(
							new InputStreamReader(is, "UTF-8"));
					while ((line = reader.readLine()) != null) {
						if (line.equals("") || line.equals("\n"))
							sb.append(line);
						else
							sb.append(line).append("\n"); // .append("\n");
					}
				} finally {
					is.close();
				}
				mol = sb.toString().trim();
			}
			method.releaseConnection();
			
			if(mol.contains("M  EN") && !mol.contains("M  END")) {
				mol = mol.replace("M  EN", "M  END");
			}
//			if(!mol.equals("0\n")) {	// (do not) change molfile properties block (first 3 rows)
//				Pattern p = Pattern.compile("[0-9]+\n[0-9]+\n");
//				mol = p.matcher(mol).replaceFirst("").trim();
//			}
			if(mol.contains("<!DOCTYPE html PUBLIC") || !mol.contains("M  END")) {	// reset mol mol data if encountered error on server
				System.err.println(id + " contains html code - return false");
				mol = "";
                                f.delete();
				return false;
			}
			if(mol.equals("0\n") || !mol.contains("M  END")){		// found no molfile
				System.err.println(id + " is empty or non-standard - return false");
				mol = "";		// reset corrupt/missing mol data to empty string
                                f.delete();
				return false;
			}
		} catch (HttpException e) {
			//e.printStackTrace();
			System.err.println("Error for ["+compound+"] !!!");
			System.err.println("HttpException occured.");
                        f.delete();
                        return false;
		} catch (IOException e) {
			//e.printStackTrace();
			System.err.println("Error for ["+compound+"] !!!");
			System.err.println("IOException occured.");
                        f.delete();
                        return false;
		}
		
		//return writeMolFile(id, mol, basePath, false);
		return writeMolFile(id, mol, basePath);
	}
	
	/**
	 * Retrieve molfile.
	 * 
	 * @param compound the name of the MassBank compound
	 * @param site the site number of the MassBank record
	 * @param record the record
	 * 
	 * @return the molfile as string
	 */
	public static String retrieveMol(String compound, String site, String record) {
		String reqStr = "";
		if(record.startsWith("CO") || record.startsWith("PB")) {
			reqStr = "http://msbi.ipb-halle.de/MassBank/" + "jsp/" + MassBankCommon.DISPATCHER_NAME;
			if(record.startsWith("CO"))
				site = "1";
			else if(record.startsWith("PB"))
				site = "0";				
		}
		else reqStr = baseUrl + "jsp/" + MassBankCommon.DISPATCHER_NAME;
//		try {
//			compound = URLEncoder.encode(compound, "UTF-8");
//			System.out.println("compound encoded -> " + compound);
//		} catch (UnsupportedEncodingException e1) {
//			e1.printStackTrace();
//			compound = compound.replaceAll(" ", "%20");
//		}
		HttpClient client = new HttpClient();
		PostMethod method = new PostMethod(reqStr);
		method.addParameter("type", "mol");
		method.addParameter("qtype", "n");
		method.addParameter("otype", "m");
		method.addParameter("query", compound);
		method.addParameter("site", site);
		
		String mol = "";
		try {
			client.executeMethod(method);
			//mol = method.getResponseBodyAsString();
			
			// getResponseBodyAsStream()
			InputStream is = method.getResponseBodyAsStream();
			StringBuilder sb = new StringBuilder();
			String line = "";
			if (is != null) {
				try {
					BufferedReader reader = new BufferedReader(
							new InputStreamReader(is, "UTF-8"));
					while ((line = reader.readLine()) != null) {
						if (line.equals("") || line.equals("\n"))
							sb.append(line);
						else
							sb.append(line).append("\n"); // .append("\n");
					}
				} finally {
					is.close();
				}
				mol = sb.toString();
			}
			method.releaseConnection();
			
			if(mol.contains("M  EN") && !mol.contains("M  END")) {
				mol = mol.replace("M  EN", "M  END");
			}
//			if(!mol.equals("0\n")) {	// (do not) change molfile properties block (first 3 rows)
//				Pattern p = Pattern.compile("[0-9]+\n[0-9]+\n");
//				Pattern p2 = Pattern.compile("[0-9]+\n[0-9]+\n\n");
//				if(p2.matcher(mol).matches())
//					mol = p2.matcher(mol).replaceFirst("");
//				else mol = p.matcher(mol).replaceFirst("");
//			}
			if(mol.contains("<!DOCTYPE html PUBLIC")) {	// reset mol mol data if encountered error on server
				mol = "";
			}
			if(mol.equals("0\n")){		// found no molfile
				mol = "";		// reset corrupt/missing mol data to empty string
			}
		} catch (HttpException e) {
			//e.printStackTrace();
			System.err.println("Error for ["+compound+"] !!!");
			System.err.println("HttpException occured.");
		} catch (IOException e) {
			//e.printStackTrace();
			System.err.println("Error for ["+compound+"] !!!");
			System.err.println("IOException occured.");
		}
		
		// wait for one second to prevent DOS for server
		try {
			Thread.sleep(1000L);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return mol;
	}
	
	/**
	 * Retrieve the MassBank site of a compound.
	 * 
	 * @param id the MassBank record id
	 * 
	 * @return the site number as string, -1 if id not found, else >= 0
	 */
	public static String retrieveSite(String id) {
		MassBankCommon mbcommon = new MassBankCommon();
		GetConfig conf = new GetConfig(baseUrl);
		serverUrl = conf.getServerUrl();
		String typeName = MassBankCommon.CGI_TBL[MassBankCommon.CGI_TBL_NUM_TYPE][MassBankCommon.CGI_TBL_TYPE_RECORD];
		ArrayList result = mbcommon.execMultiDispatcher( serverUrl, typeName, "id=" + id );
		String line = "";
		Boolean isFound = false;
		String[] val = null;
		for ( int i = 0; i < result.size(); i++ ) {
			line = (String)result.get(i);
			if ( !line.equals("") ) {
				val = line.split("\t");
				if ( val[0].equals(id) ) {
					isFound = true;
					break;
				}
			}
		}
		if ( !isFound ) {
			return "-1";
		}
		else {
			return val[1];
		}
	}
	
	/**
	 * Fetch record.
	 * 
	 * @param id the id
	 * @param site the site
	 */
	public static void fetchRecord(String id, String site) {
		String prefix = id.substring(0, 2);
		File dir = new File(cacheMassBank);
		String[] institutes = dir.list();
		File f = null;
        boolean found = false;
        System.out.println("ID -> " + id + "  site -> " + site);
		for (int i = 0; i < institutes.length; i++) {
			if(institutes[i].startsWith(prefix)) {
                f = new File(dir, institutes[i] + "/records/" + id + ".txt");
                found = true;
                //break;
                return;     // return if record was found
            }
		}

//                if(!found) {
//                    f = new File(dir, prefix);
//                    boolean createDir = false;
//                    if(!f.exists())
//                        createDir = f.mkdir();
//
//                    if(createDir)
//                        retrieveRecord(id, site);
//                    else return;
//                }
                
	    if(f == null) {
	        System.err.println("ID " + id + " cannot be retrieved!");
	        return;
	    }
//	    else if(f != null && !f.exists()) {
//			System.out.println(f + " does not exist - going to create it.");
//			retrieveRecord(id, site);
//		}
    }
	
	/**
	 * Retrieve record.
	 * 
	 * @param id the id
	 * @param site the site
	 * 
	 * @return the string
	 * 
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static String retrieveRecord(String id, String site) {
		String content = "";
		
		String prefix = id.substring(0, 2);
		File dir = new File(cacheMassBank);
		String[] institutes = dir.list();
		File f = null;
		for (int i = 0; i < institutes.length; i++) {
			if(institutes[i].startsWith(prefix))
				f = new File(dir, institutes[i] + "/records/" + id + ".txt");
		}
		//File f = new File("/home/mgerlich/workspace-3.5/MassBankComparison/MBCache/" + id + ".txt");
		if(!f.exists()) {
			String reqStr = "http://msbi.ipb-halle.de/MassBank/";
			//String reqStr = "http://www.massbank.jp/";
			reqStr += "jsp/" + MassBankCommon.DISPATCHER_NAME;
			
			HttpClient client = new HttpClient();
			PostMethod method = new PostMethod( reqStr );
			method.addParameter("type", "disp");		// display record
			method.addParameter("id", id);				// specify record
			method.addParameter("site", site);
			
			try {
				client.executeMethod(method);
				//String result1 = method.getResponseBodyAsString();
				String result1 = "";
				
				// getResponseBodyAsStream()
				InputStream is = method.getResponseBodyAsStream();
				StringBuilder sb = new StringBuilder();
				String line = "";
				if (is != null) {
					try {
						BufferedReader reader = new BufferedReader(
								new InputStreamReader(is, "UTF-8"));
						while ((line = reader.readLine()) != null) {
							if (line.equals("") || line.equals("\n"))
								sb.append(line);
							else
								sb.append(line).append("\n"); // .append("\n");
						}
					} finally {
						is.close();
					}
					result1 = sb.toString();
				}
				
				method.releaseConnection();
				
				if(result1.contains("ACCESSION")) {
					String result = result1.substring(result1.indexOf("ACCESSION"), result1.indexOf("</pre>"));
					FileWriter fw = new FileWriter(f);
					fw.write(result);
					fw.flush();
					fw.close();
					
					return result;
				}
				else return "";
				
			}
			catch(IOException e) {
				
			}
			
			
		}
		
		System.out.println("Record " + id + " exists!");
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(f));
			String line = "";
			content = "";
			while((line = br.readLine()) != null) {
				content += line;
			}
			br.close();
			
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return content;
	}
	
	/**
	 * Retrieve links.
	 * 
	 * @param id the id
	 * @param site the site
	 * 
	 * @return the map< string, string>
	 */
	public static Map<String, String> retrieveLinks(String id, String site) {
		Map<String, String> dbs = new HashMap<String, String>();
		
//		File f = new File("/home/mgerlich/workspace-3.5/MassBankComparison/MBCache/" + id + ".txt");
		String prefix = "";
		if(id.matches("[A-Z]{3}[0-9]{5}"))
			prefix = id.substring(0, 3);
		else prefix = id.substring(0, 2);
		
		File dir = new File(cacheMassBank);
		String[] institutes = dir.list();
		File f = null;
		boolean found = false;
		for (int i = 0; i < institutes.length; i++) {
			if(institutes[i].startsWith(prefix)) {
				f = new File(dir, institutes[i] + "/records/" + id + ".txt");
				found = true;
				break;
			}
		}
		if(!found) {
          f = new File(dir, prefix);
          boolean createDir = false;
          if(!f.exists())
              createDir = f.mkdir();
          System.out.println("created directory [" + f.getAbsolutePath() + "] -> " + createDir);
          File molDir = new File(f, "mol");
          File recDir = new File(f, "records");
          System.out.println("molDir ? " + molDir.mkdir() + "\trecDir ? " + recDir.mkdir());
          
          f = new File(dir, prefix + "/records/" + id + ".txt");
         // if(createDir)
         //     retrieveRecord(id, site);
         // else return;
		}
		
		if(f != null && !f.exists()) {
			String reqStr = "http://msbi.ipb-halle.de/MassBank/";
			reqStr += "jsp/" + MassBankCommon.DISPATCHER_NAME;
			
			HttpClient client = new HttpClient();
			PostMethod method = new PostMethod( reqStr );
			method.addParameter("type", "disp");		// display record
			method.addParameter("id", id);				// specify record
			method.addParameter("site", site);
			
			try {
				client.executeMethod(method);
				//String result1 = method.getResponseBodyAsString();
				
				String result1 = "";
				// getResponseBodyAsStream()
				InputStream is = method.getResponseBodyAsStream();
				StringBuilder sb = new StringBuilder();
				String line = "";
				if (is != null) {
					try {
						BufferedReader reader = new BufferedReader(
								new InputStreamReader(is, "UTF-8"));
						while ((line = reader.readLine()) != null) {
							if (line.equals("") || line.equals("\n"))
								sb.append(line);
							else
								sb.append(line).append("\n"); // .append("\n");
						}
					} finally {
						is.close();
					}
					result1 = sb.toString();
				}
				
				if(result1.contains("ACCESSION") && result1.contains("</pre>")) {
					String result = result1.substring(result1.indexOf("ACCESSION"), result1.indexOf("</pre>"));
					FileWriter fw = new FileWriter(f);
					fw.write(result);
					fw.flush();
					fw.close();
				}
                                else return dbs;    // invalid/empty MassBank record -> skip and return empty map
			} catch (HttpException e) {
				System.err.println("HTTP Exception while trying to fetch " + id);
				e.printStackTrace();
			} catch (IOException e) {
				System.err.println("IO Exception while trying to write record " + id + " to disk.");
				e.printStackTrace();
			}
			method.releaseConnection();
		}
		
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(f));
		} catch (FileNotFoundException e) {
                        System.err.println("File " + f.getAbsolutePath() + " not found!");
			e.printStackTrace();
                        return dbs;     // cancel reading of file and return empty hashmap
		}
		String line = "";
		String db = "";
		String val = "";
		try {
			while((line = br.readLine()) != null) {
				if(line.startsWith("CH$LINK:")) {
					String[] split = line.split(" ");
					db = split[1];
					for (int i = 2; i < split.length; i++) {
						if(split[i].contains("</a>")) {
							val = split[i].substring(split[i].indexOf("\"_blank\">") + 9, split[i].indexOf("</a>"));
							break;
						}
					}
					dbs.put(db, val);
				}
				else if (line.startsWith("CH$SMILES")) {
					val = line.substring(line.indexOf(":") + 1).trim();
					dbs.put("smiles", val);
				}
				else if (line.startsWith("CH$IUPAC")) {
					val = line.substring(line.indexOf(":") + 1).trim();
					dbs.put("inchi", val);
				}
			}
		} catch (IOException e) {
//			e.printStackTrace();
		}
		try {
			br.close();
		} catch (IOException e) {
//			e.printStackTrace();
		}
		
		return dbs;
	}
	
	public static double retrieveExactMass(String id, String site) {
		double emass = 0.0d;
		
		String prefix = "";
		if(id.matches("[A-Z]{3}[0-9]{5}"))
			prefix = id.substring(0, 3);
		else prefix = id.substring(0, 2);
		
		File dir = new File(cacheMassBank);
		String[] institutes = dir.list();
		File f = null;
		boolean found = false;
		for (int i = 0; i < institutes.length; i++) {
			if(institutes[i].startsWith(prefix)) {
				f = new File(dir, institutes[i] + "/records/" + id + ".txt");
				found = true;
				break;
			}
		}

		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(f));
		} catch (FileNotFoundException e) {
			System.err.println("File " + f.getAbsolutePath() + " not found!");
			e.printStackTrace();
			return emass; // cancel reading of file and return empty hashmap
		}
		String line = "";
		try {
			while ((line = br.readLine()) != null) {
				if (line.startsWith("CH$EXACT_MASS:")) {
					emass = Double.parseDouble(line.substring(line.indexOf(":") + 1).trim());
				}
			}
		} catch (NumberFormatException e) {
			System.err.println("Error converting String mass to double mass!");
			System.err.println("line -> " + line);
			return 0d;	// return zero mass
		}
		catch (IOException e) {
			// e.printStackTrace();
		}
		try {
			br.close();
		} catch (IOException e) {
			// e.printStackTrace();
		}
		
		return emass;
	}
	
	/**
	 * Write container to file.
	 * 
	 * @param f the file for the mol data
	 * @param container the container holding the IChemObject
	 * 
	 * @return true, if successful
	 */
	public static boolean writeContainer(File f, IAtomContainer container) {
		if(f.exists()) {
			System.out.println("File " + f.getAbsolutePath() + " already exists - returning!");
			return true;
		}
		
		try {
			MDLV2000Writer writer = new MDLV2000Writer(new FileWriter(f));
			
			/**
			 *  hydrogen handling
			 */
			try {
				AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(container);
				CDKHydrogenAdder hAdder = CDKHydrogenAdder.getInstance(container.getBuilder());
                                hAdder.addImplicitHydrogens(container);
                                AtomContainerManipulator.convertImplicitToExplicitHydrogens(container);
			} catch (CDKException e) {
				System.err.println("error manipulating mol for " + f.getAbsolutePath());
			}
			
			writer.write(container);
			writer.close();
			
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (CDKException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Retrieves the IAtomContainer for a moldata string.
	 * 
	 * @param mol the moldata as String
	 * 
	 * @return the container as IAtomContainer object, null if Exception occurs
	 */
	public static IAtomContainer getContainer(String mol) {
		if(mol.isEmpty() || mol.equals("0\n")) {
			return null;
		}
		InputStream is = new ByteArrayInputStream(mol.getBytes());
		MDLV2000Reader reader = new MDLV2000Reader(is);
		IChemFile chemFile = new ChemFile();
		IAtomContainer container = null;
		try {			
			chemFile = (IChemFile) reader.read(chemFile);
			container = ChemFileManipulator.getAllAtomContainers(chemFile).get(0);
			
			/**
			 *  hydrogen handling
			 */
			try {
				AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(container);
				CDKHydrogenAdder hAdder = CDKHydrogenAdder.getInstance(container.getBuilder());
		        hAdder.addImplicitHydrogens(container);
		        AtomContainerManipulator.convertImplicitToExplicitHydrogens(container);
			} catch (CDKException e) {
				return container;
			}
		} catch (NumberFormatException e) {
			System.err.println("NumberFormatException occured while parsing mol file");
			return null;
		} catch (CDKException e) {
//			e.printStackTrace();
			return null;
		}
		return container;
	}
	
	/**
	 * Gets the container.
	 * 
	 * @param id the id
	 * @param basePath the base path
	 * 
	 * @return the container
	 */
	public static IAtomContainer getContainer(String id, String basePath) {
		File f = new File(basePath, id + ".mol");
		if(!f.exists()) {
			System.out.println("mol path -> " + f + "\t=> exists ? " + f.exists());
			return null;
		}
		
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(f);
		} catch (FileNotFoundException e1) {
			System.err.println("File not found - " + f);
			return null;
		}
		InputStream is = fis;
		MDLV2000Reader reader = new MDLV2000Reader(is);
		reader.setErrorHandler(new ErrorHandler());
		IChemFile chemFile = new ChemFile();
		IAtomContainer container = null;
		try {			
			chemFile = (IChemFile) reader.read(chemFile);
                        List<IAtomContainer> containers = ChemFileManipulator.getAllAtomContainers(chemFile);
                        if(containers != null && containers.size() > 0)
                            container = ChemFileManipulator.getAllAtomContainers(chemFile).get(0);
                        else return null;
                        
			/**
			 *  hydrogen handling
			 */
			try {
				AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(container);
				CDKHydrogenAdder hAdder = CDKHydrogenAdder.getInstance(container.getBuilder());
		        hAdder.addImplicitHydrogens(container);
		        AtomContainerManipulator.convertImplicitToExplicitHydrogens(container);
			} catch (CDKException e) {
				System.err.println("error manipulating mol for " + id);
			}
			// remove hydrogens
//			container = AtomContainerManipulator.removeHydrogens(container);
		} catch (java.lang.NumberFormatException e) {
			System.err.println("NumberFormatException occured while parsing mol file - " + f);
			f.delete();	// delete erroneous file if possible
			return null;
		} catch (CDKException e) {
			System.err.println("CDKException occured for mol file - " + f);
			f.delete();	// delete erroneous file if possible
			return null;
		}
		
		return container;
	}
	
	/**
	 * Gets the unmodified (implicit/explicit hydrogen conversion) container.
	 * 
	 * @param id the id
	 * @param basePath the base path
	 * 
	 * @return the container
	 */
	public static IAtomContainer getContainerUnmodified(String id, String basePath) {
		File f = new File(basePath, id + ".mol");
		if(!f.exists()) {
			System.out.println("mol path -> " + f + "\t=> exists ? " + f.exists());
			return null;
		}
		
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(f);
		} catch (FileNotFoundException e1) {
			System.err.println("File not found - " + f);
			return null;
		}
		InputStream is = fis;
		MDLReader reader = new MDLReader(is);
		//reader.setErrorHandler(new ErrorHandler());
		IChemFile chemFile = new ChemFile();
		IAtomContainer container = null;
		try {			
			chemFile = (IChemFile) reader.read(chemFile);
            List<IAtomContainer> containers = ChemFileManipulator.getAllAtomContainers(chemFile);
            if(containers != null && containers.size() > 0)
                container = ChemFileManipulator.getAllAtomContainers(chemFile).get(0);
            else return null;
		} catch (java.lang.NumberFormatException e) {
			System.err.println("NumberFormatException occured while parsing mol file - " + f);
			f.delete();	// delete erroneous file if possible
			return null;
		} catch (CDKException e) {
			System.err.println("CDKException occured for mol file - " + f);
			f.delete();	// delete erroneous file if possible
			return null;
		}
		
		return container;
	}
	
	/**
	 * Gets the mol from smiles.
	 * 
	 * @param smiles the smiles
	 * 
	 * @return the mol from smiles
	 */
	public static IAtomContainer getMolFromSmiles(String smiles) {
            if(smiles == null || smiles.isEmpty())
                return null;
            
            IAtomContainer container = null;
            try {
                SmilesParser sp = new SmilesParser(DefaultChemObjectBuilder.getInstance());
                IMolecule m = sp.parseSmiles(smiles);
                container = m;

                /**
                 *  hydrogen handling
                 */
                try {
                    AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(container);
                    CDKHydrogenAdder hAdder = CDKHydrogenAdder.getInstance(container.getBuilder());
                    hAdder.addImplicitHydrogens(container);
                    AtomContainerManipulator.convertImplicitToExplicitHydrogens(container);
                } catch (CDKException e) {
                    System.err.println("error manipulating mol for smiles");
                }

                // remove hydrogens
//			container = AtomContainerManipulator.removeHydrogens(container);
            } catch (InvalidSmilesException ise) {
//			ise.printStackTrace();
                    return null;
            }
            return container;
	}
	
	/**
	 * Gets the mol from inchi.
	 * 
	 * @param inchi the inchi
	 * 
	 * @return the mol from inchi
	 * 
	 * @throws CDKException the CDK exception
	 */
	public static IAtomContainer getMolFromInchi(String inchi) throws CDKException {
		IAtomContainer container = null;
		// Generate factory - throws CDKException if native code does not load
		InChIGeneratorFactory factory = InChIGeneratorFactory.getInstance();
		// Get InChIToStructure
		InChIToStructure intostruct = factory.getInChIToStructure(inchi, null);
		
		INCHI_RET ret = intostruct.getReturnStatus();
		if (ret == INCHI_RET.WARNING) {
			// Structure generated, but with warning message
			System.out.println("InChI warning: " + intostruct.getMessage());
		} else if (ret != INCHI_RET.OKAY) {
			// Structure generation failed
			throw new CDKException("Structure generation failed failed: "
					+ ret.toString() + " [" + intostruct.getMessage() + "]");
		}

		container = intostruct.getAtomContainer();
		
		/**
		 *  hydrogen handling
		 */
		try {
			AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(container);
			CDKHydrogenAdder hAdder = CDKHydrogenAdder.getInstance(container.getBuilder());
	        hAdder.addImplicitHydrogens(container);
	        AtomContainerManipulator.convertImplicitToExplicitHydrogens(container);
		} catch (CDKException e) {
			System.err.println("error manipulating mol for inchi");
		}
		return container;
	}
	
	/**
	 * Gets the mol from any.
	 * 
	 * @param id the id
	 * @param basePath the base path
	 * @param smiles the smiles
	 * 
	 * @return the mol from any
	 */
	public static IAtomContainer getMolFromAny(String id, String basePath, String smiles) {
		File f = new File(basePath, id + ".mol");
		System.out.println("mol path -> " + f);
		System.out.println("exists ? " + f.exists());
		if(!f.exists() && !smiles.isEmpty() && smiles != null) {
			IAtomContainer container = getMolFromSmiles(smiles);
			SDFWriter writer = new SDFWriter();
			try {
				writer.setWriter(new FileOutputStream(f));
				writer.write(container);
				writer.close();
			} catch (FileNotFoundException e) {
				//e.printStackTrace();
				System.err.println("File not found - " + f);
				return null;
			} catch (NumberFormatException e) {
				System.err.println("NumberFormatException occured while parsing mol file - " + f);
				return null;
			} catch (CDKException e) {
				//e.printStackTrace();
				System.err.println("CDKException occured for mol file - " + f);
				return null;
			} catch (IOException e) {
				//e.printStackTrace();
				System.err.println("IOException occured for mol file - " + f);
				return null;
			} 
			
			return container;
		}
		else return getContainer(id, basePath);
	}
	
	/**
	 * Gets the mol from any.
	 * 
	 * @param id the id
	 * @param basePath the base path
	 * @param smiles the smiles
	 * @param name the name
	 * @param site the site
	 * 
	 * @return the mol from any
	 */
	public static IAtomContainer getMolFromAny(String id, String basePath, String smiles, String name, String site) {
		IAtomContainer container = getMolFromAny(id, basePath, smiles);
		
		if(container == null) {
			boolean success = fetchMol(name, id, site, basePath);
			if(success) {
				container = getContainer(id, basePath);
			}
			else {
				return null;		// all attempts have failed -> ignore record prospectively
			}
		}
		
		return container;
	}
}