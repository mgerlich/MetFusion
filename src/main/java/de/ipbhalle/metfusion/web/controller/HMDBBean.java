/**
 * created by Michael Gerlich, Jan 29, 2013 - 1:34:44 PM
 */ 

package de.ipbhalle.metfusion.web.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openscience.cdk.interfaces.IAtomContainer;

import de.ipbhalle.metfusion.utilities.MassBank.MassBankUtilities;
import de.ipbhalle.metfusion.wrapper.Result;

public class HMDBBean implements GenericDatabaseBean {

	private String databaseName = "HMDB";
	private String structurePath = "/home/mgerlich/Downloads/HMDB/structures/";
	private final String urlHMDB = "http://www.hmdb.ca/";
	
	private String sessionPath;
	private String serverUrl;
	private List<Result> results;
	private List<Result> unused;
	private boolean done;
	private boolean showResult;
	private boolean showNote;
	private boolean uniqueInchi;
	private int searchProgress;
	
	private final String utf8 = "✓";	// %E2%9C%93
	
	/** HMDB search types */
	public enum searchType {MS, MSMS, GCMS, NMR1D, NMR2D};
	
	/** 1D NMR types */
	public enum nucleus {H, C};
	private final String prefixH = "1";
	private final String prefixC = "13";
	private String queryUrlNMR1D = "http://www.hmdb.ca/spectra/nmr_one_d/search?" +
			"utf8=" + utf8 + "&nucleus=%s" + "&peaks=%s" +
			"&cs_tolerance=%f" + "&commit=Search";
	private final String tableNMR1D = "nmr_one_d_search_results";
	private String libraryNMR1D = prefixH + nucleus.H.toString();
	private String peaksNMR1D = "3.81\n3.82\n3.83\n3.85\n3.89\n3.90\n3.91\n4.25\n4.26\n4.27\n4.41\n8.19\n8.31";
	private float toleranceNMR1D = 0.02f;
	
	/** 2D NMR types */
	public enum library {tocsy, hsqc};
	private String queryUrlNMR2D = "http://www.hmdb.ca/spectra/nmr_two_d/search?" +
			"utf8=" + utf8 + "&library=%s" + "&peaks=%s" +
			"&x_tolerance=%f" + "&y_tolerance=%f" +	"&commit=Search";
	private final String tableNMR2D = "nmr_two_d_search_results";
	private String libraryNMR2D = library.tocsy.toString();
	private String peaksNMR2D = "3.76 2.126\n3.76 2.446\n3.76 3.76\n2.446 2.126\n2.446 2.446\n2.446 3.76\n2.126 2.126\n2.126 2.446\n2.126 3.76";
	private float toleranceNMR2Dx = 0.02f;
	private float toleranceNMR2Dy = 0.02f;
	
	/** MS search ionization modes */
	public enum msIon {positive, negative, neutral};
	/** MS query URL */
	private String queryUrlMS = "http://www.hmdb.ca/spectra/ms/search?" +
			"utf8=" + utf8 + "&query_masses=%s" + "&tolerance=%f" +	"&mode=%s" + "&commit=Search";
	private final String tableMS = "ms-search-result"; 	// ms-search-table, gibt mehrere je nach anfrage
	private String peaksMS = "175\n209.98\n2011.971";
	private float toleranceMS = 0.05f;
	private String modeMS = msIon.positive.toString();
	
	/** MSMS search ionization modes */
	public enum msmsIon {Positive, Negative, NA};
	/** MSMS search collision energies */
	public enum msmsCE {Low, Medium, High, All};
	/** MSMS query URL */
	private String queryUrlMSMS = "http://www.hmdb.ca/spectra/ms_ms/search?" +
			"utf8=" + utf8 + "&parent_ion_mass=%f" + "&parent_ion_mass_tolerance=%f" +
			"&ionization_mode=%s" +	"&collision_energy_level=%s" +
			"&peaks=%s" + "&mass_charge_tolerance=%f" +	"&commit=Search";
	private final String tableMSMS = "ms_ms_search_results";
	private float precursorMSMS = 146.0f;
	private float toleranceMSMSprecursor = 0.1f;
	private String modeMSMS = msmsIon.Positive.toString();
	private String ceMSMS = msmsCE.Low.toString();
	private String peaksMSMS = "40.948 0.174\n56.022 0.424\n84.37 53.488\n101.50 8.285\n102.401 0.775\n129.670 100.000\n146.966 20.070";
	private float toleranceMSMSmz = 0.5f;
		
	/** GCMS search types */
	public enum gcmsType {retention_index, retention_time};
	private String queryUrlGCMS = "http://www.hmdb.ca/spectra/c_ms/search?" +
			"utf8=" + utf8 + "&retention_type=%s" + "&retention=%f" + "&retention_tolerance=%f" +
			"&peaks=%s" + "&mass_charge_tolerence=%f" +	"&commit=Search";
	private final String tableGCMS = "index";		// suche nach class index oder tag table
	private String typeGCMS = gcmsType.retention_index.toString();
	private float retentionGCMS = 1072.0f;
	private float toleranceGCMSretention = 1f;
	private String peaksGCMS = "73\n147\n117\n190\n191\n148\n66\n75";
	private float toleranceGCMSmz = 0.0f;
	
	
	public HMDBBean() {
		
	}
	
	@Override
	public void run() {
		List<Result> results = performQuery(searchType.NMR2D);
		System.out.println("#results -> " + results.size());
	}

	public List<Result> performQuery(searchType type) {
		List<Result> results = new ArrayList<Result>();
		String query = formatURL(type);
		
		results = parseGCandNMRResults(query, type);
		
		setResults(results);
		
		return results;
	}
	
	private String formatURL(searchType type) {
		String formatted = "";
		
		switch(type) {
		case MS:
			formatted = String.format(queryUrlMS, encodeValue(peaksMS), toleranceMS, modeMS);
			break;
			
		case MSMS:
			formatted = String.format(queryUrlMSMS, precursorMSMS, toleranceMSMSprecursor, modeMSMS, ceMSMS, encodeValue(peaksMSMS), toleranceMSMSmz);
			break;

		case GCMS:
			formatted = String.format(queryUrlGCMS, typeGCMS, retentionGCMS, toleranceGCMSretention, encodeValue(peaksGCMS), toleranceGCMSmz);
			break;

		case NMR1D:
			if("C".equals(nucleus.C))
				libraryNMR1D = prefixC + "C";
			else if("H".equals(nucleus.H)) {
				libraryNMR1D = prefixH + "H";
			}
			else libraryNMR1D = prefixH + "H";	// default to H
			
			formatted = String.format(queryUrlNMR1D, libraryNMR1D, encodeValue(peaksNMR1D), toleranceNMR1D);
			break;

		case NMR2D:
			formatted = String.format(queryUrlNMR2D, libraryNMR2D, encodeValue(peaksNMR2D), toleranceNMR2Dx, toleranceNMR2Dy);
			break;

		default: System.err.println("No defined search type!");		
		}
		
		
		return formatted;
	}
	
	private String encodeValue(String value) {
		String encoded = "";
		try {
			encoded = URLEncoder.encode(value, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			System.err.println("Unsupported encoding found for URLEncoder!");
			encoded = value;
		}
		return encoded;
	}
	
	private List<Result> parseGCandNMRResults(String url, searchType type) {
		List<Result> results = new ArrayList<Result>();
		// parse HTML with jsoup
		Document doc = null;
		try {
			doc = Jsoup.connect(url).get();
		} catch (IOException e) {
			System.err.println("Error connecting to HMDB!");
			return results;
		}
		
		// table rows
		//Elements rows = doc.getElementsByTag("table");	// first row contains header <th>, afterwards only data <td>
		//Element table = doc.getElementById("nmr_two_d_search_results");	// first row contains header <th>, afterwards only data <td>
		
		// GCMS, NMR1D + NMR2D provide the same result table style (5 columns)
		// MSMS has 7 columns
		// MS has 6 columns but multiple tables
		Element table = null;
		Elements tablesMS = null;
		switch(type) {
		
		case MS: 
			table = doc.getElementsByClass(tableMS).first(); 
			tablesMS = doc.getElementsByClass(tableMS); 
			break;
		case MSMS: table = doc.getElementById(tableMSMS); break;
		case NMR1D: table = doc.getElementById(tableNMR1D); break;
		case NMR2D: table = doc.getElementById(tableNMR2D); break;
		case GCMS: table = doc.getElementsByClass(tableGCMS).first(); break;
		default: System.err.println("Unsupported search type!"); break;
		}

		if(table == null)
			return results;
		
		// if MS search mit multiple entries
		if(type.equals(searchType.MS)) {
			for (Element element : tablesMS) {
				results = queryMSTables(element, results);
			}
			return results;
		}
		
		Elements rows = table.getElementsByTag("tr");
		MassBankUtilities mbu = new MassBankUtilities(structurePath);
		
		for (Element element : rows) {
			Elements data = element.getElementsByTag("td");
			int columnCounter = 0;
			String id = "";				// HMDB ID
			String name = "";			// name
			String cas = "";			// CAS ID
			String formula = "";		// molecular formula
			double weight = 0.0d;		// molecular weight
			double score = 0.0d;		// library matches
			String link = "";
			if(!data.isEmpty()) {
				for (Element d : data) {
					String s = d.toString();
					// link + ID
					if(columnCounter == 0 && s.contains("<a")) {
						id = d.text();
						link = d.getElementsByTag("a").get(0).attr("href");
					}
					
					// name + CAS
					if(columnCounter == 1 && s.contains("<strong>")) {
						String[] split = d.text().trim().split(" ");
						if(split.length > 2) {	// name has two or more entries
							for (int i = 0; i < split.length - 1; i++) {
								name += split[i] + " ";
							}
							cas = split[split.length-1];
						}
						else if(split.length == 2) {
							name = split[0];
							cas = split[1];
						}
						
						name = name.trim();
					}
					
					// formula + weight
					if(columnCounter == 2 && s.contains("<sub>")) {
						String[] split = d.text().trim().split(" ");
						if(split.length == 2) {
							weight = Double.parseDouble(split[0]);
							formula = split[1];
						}
					}
					
					// image - > columnCounter == 3
					
					// score in the form of x/y
					if(columnCounter == 4) {
						String s2 = d.text().trim();
						if(s2.contains("/")) {
							String[] split = s2.split("/");
							if(split.length == 2) {
								double d1 = Double.parseDouble(split[0]);
								double d2 = Double.parseDouble(split[1]);
								score = d1 / d2;
							}
						}
						else score = Double.parseDouble(d.text().trim());
					}
					columnCounter++;
				}
				
				IAtomContainer ac = mbu.getContainer(id, structurePath);
				
				Result r = new Result(databaseName, id, name, score, ac, urlHMDB + link, "image", formula, weight);
				results.add(r);
			}
		}
		
		return results;
	}
	
	private List<Result> queryMSTables(Element table, List<Result> current) {
		List<Result> results = current;
		
		Elements rows = table.getElementsByTag("tr");
		String structurePath = "/home/mgerlich/Downloads/HMDB/structures/";
		String urlHMDB = "http://www.hmdb.ca/";
		MassBankUtilities mbu = new MassBankUtilities(structurePath);
		
		for (Element element : rows) {
			Elements data = element.getElementsByTag("td");
			int columnCounter = 0;
			String id = "";				// HMDB ID
			String name = "";			// name
			String adduct = "";			// adduct
			double adductWeith = 0.0d;	// adduct MW
			String formula = "";		// molecular formula
			double weight = 0.0d;		// molecular weight
			double delta = 0.0d;		// Delta = abs( query mass - adduct mass )
			String link = "";
			if(!data.isEmpty()) {
				for (Element d : data) {
					String s = d.toString();
					// link + ID
					if(columnCounter == 0 && s.contains("<a")) {
						id = d.text();
						link = d.getElementsByTag("a").get(0).attr("href");
					}
					else if(columnCounter == 1) {	// name
						name = d.text().trim();
					}
					else if(columnCounter == 2 ) {	// adduct
						adduct = d.text().trim();
						formula = adduct;
					}
					else if(columnCounter == 3) {	// adduct MW
						adductWeith = Double.parseDouble(d.text().trim());
					}
					else if(columnCounter == 4) {	// compound MW
						weight = Double.parseDouble(d.text().trim());
					}
					else if(columnCounter == 5) {	// delta
						delta = Double.parseDouble(d.text().trim());
					}
					
					columnCounter++;
				}
				
				IAtomContainer ac = mbu.getContainer(id, structurePath);
				
				Result r = new Result(databaseName, id, name, delta, ac, urlHMDB + link, "image", formula, weight);
				results.add(r);
			}
		}
		
		return results;
	}
	
	public static void main(String[] args) throws IOException {
		
		HMDBBean hb = new HMDBBean();
		List<Result> results = hb.performQuery(HMDBBean.searchType.MS);
		System.out.println("Found [" + results.size() + "] results");
		System.out.println("ID\tName\tFormula\tWeight\tScore");
		for (Result result : results) {
			System.out.println(result.getId() + "\t" + result.getName() + "\t" + result.getSumFormula() + 
					"\t" + result.getExactMass() + "\t" + result.getScore());
		}
	}
	
	@Override
	public String getDatabaseName() {
		return databaseName;
	}

	@Override
	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	@Override
	public String getSessionPath() {
		return sessionPath;
	}

	@Override
	public void setSessionPath(String sessionPath) {
		this.sessionPath = sessionPath;
	}

	@Override
	public String getServerUrl() {
		return serverUrl;
	}

	@Override
	public void setServerUrl(String serverUrl) {
		this.serverUrl = serverUrl;
	}

	@Override
	public List<Result> getResults() {
		return results;
	}

	@Override
	public void setResults(List<Result> results) {
		this.results = results;
	}

	@Override
	public List<Result> getUnused() {
		return unused;
	}

	@Override
	public void setUnused(List<Result> unused) {
		this.unused = unused;
	}

	@Override
	public boolean isDone() {
		return done;
	}

	@Override
	public void setDone(boolean done) {
		this.done = done;
	}

	@Override
	public boolean isShowResult() {
		return showResult;
	}

	@Override
	public void setShowResult(boolean showResult) {
		this.showResult = showResult;
	}

	@Override
	public boolean isShowNote() {
		return showNote;
	}

	@Override
	public void setShowNote(boolean showNote) {
		this.showNote = showNote;
	}

	@Override
	public boolean isUniqueInchi() {
		return uniqueInchi;
	}

	@Override
	public void setUniqueInchi(boolean uniqueInchi) {
		this.uniqueInchi = uniqueInchi;
	}

	@Override
	public int getSearchProgress() {
		return searchProgress;
	}

	@Override
	public void setSearchProgress(int searchProgress) {
		this.searchProgress = searchProgress;
	}

	public String getPeaksNMR1D() {
		return peaksNMR1D;
	}

	public void setPeaksNMR1D(String peaksNMR1D) {
		this.peaksNMR1D = peaksNMR1D;
	}

	public float getToleranceNMR1D() {
		return toleranceNMR1D;
	}

	public void setToleranceNMR1D(float toleranceNMR1D) {
		this.toleranceNMR1D = toleranceNMR1D;
	}

	public String getLibraryNMR2D() {
		return libraryNMR2D;
	}

	public void setLibraryNMR2D(String libraryNMR2D) {
		this.libraryNMR2D = libraryNMR2D;
	}
	
	public String getPeaksNMR2D() {
		return peaksNMR2D;
	}

	public void setPeaksNMR2D(String peaksNMR2D) {
		this.peaksNMR2D = peaksNMR2D;
	}

	public float getToleranceNMR2Dx() {
		return toleranceNMR2Dx;
	}

	public void setToleranceNMR2Dx(float toleranceNMR2Dx) {
		this.toleranceNMR2Dx = toleranceNMR2Dx;
	}

	public float getToleranceNMR2Dy() {
		return toleranceNMR2Dy;
	}

	public void setToleranceNMR2Dy(float toleranceNMR2Dy) {
		this.toleranceNMR2Dy = toleranceNMR2Dy;
	}

	public String getLibraryNMR1D() {
		return libraryNMR1D;
	}

	public void setLibraryNMR1D(String libraryNMR1D) {
		this.libraryNMR1D = libraryNMR1D;
	}
	
	public String getPeaksMS() {
		return peaksMS;
	}

	public void setPeaksMS(String peaksMS) {
		this.peaksMS = peaksMS;
	}

	public float getToleranceMS() {
		return toleranceMS;
	}

	public void setToleranceMS(float toleranceMS) {
		this.toleranceMS = toleranceMS;
	}

	public String getModeMS() {
		return modeMS;
	}

	public void setModeMS(String modeMS) {
		this.modeMS = modeMS;
	}

	public float getPrecursorMSMS() {
		return precursorMSMS;
	}

	public void setPrecursorMSMS(float precursorMSMS) {
		this.precursorMSMS = precursorMSMS;
	}

	public float getToleranceMSMSprecursor() {
		return toleranceMSMSprecursor;
	}

	public void setToleranceMSMSprecursor(float toleranceMSMSprecursor) {
		this.toleranceMSMSprecursor = toleranceMSMSprecursor;
	}

	public String getModeMSMS() {
		return modeMSMS;
	}

	public void setModeMSMS(String modeMSMS) {
		this.modeMSMS = modeMSMS;
	}

	public String getCeMSMS() {
		return ceMSMS;
	}

	public void setCeMSMS(String ceMSMS) {
		this.ceMSMS = ceMSMS;
	}

	public String getPeaksMSMS() {
		return peaksMSMS;
	}

	public void setPeaksMSMS(String peaksMSMS) {
		this.peaksMSMS = peaksMSMS;
	}

	public float getToleranceMSMSmz() {
		return toleranceMSMSmz;
	}

	public void setToleranceMSMSmz(float toleranceMSMSmz) {
		this.toleranceMSMSmz = toleranceMSMSmz;
	}

	public String getTypeGCMS() {
		return typeGCMS;
	}

	public void setTypeGCMS(String typeGCMS) {
		this.typeGCMS = typeGCMS;
	}

	public float getRetentionGCMS() {
		return retentionGCMS;
	}

	public void setRetentionGCMS(float retentionGCMS) {
		this.retentionGCMS = retentionGCMS;
	}

	public float getToleranceGCMSretention() {
		return toleranceGCMSretention;
	}

	public void setToleranceGCMSretention(float toleranceGCMSretention) {
		this.toleranceGCMSretention = toleranceGCMSretention;
	}

	public String getPeaksGCMS() {
		return peaksGCMS;
	}

	public void setPeaksGCMS(String peaksGCMS) {
		this.peaksGCMS = peaksGCMS;
	}

	public float getToleranceGCMSmz() {
		return toleranceGCMSmz;
	}

	public void setToleranceGCMSmz(float toleranceGCMSmz) {
		this.toleranceGCMSmz = toleranceGCMSmz;
	}

	public String getStructurePath() {
		return structurePath;
	}

	public void setStructurePath(String structurePath) {
		this.structurePath = structurePath;
	}

}
