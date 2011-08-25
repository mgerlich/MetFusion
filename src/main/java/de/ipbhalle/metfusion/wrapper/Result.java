/**
 * created by Michael Gerlich on May 20, 2010
 * last modified May 20, 2010 - 4:43:23 PM
 * email: mgerlich@ipb-halle.de
 */
package de.ipbhalle.metfusion.wrapper;

import java.text.DecimalFormat;
import java.util.BitSet;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.fingerprint.Fingerprinter;
import org.openscience.cdk.interfaces.IAtomContainer;

/**
 * The Class Result. It contains information about the name of the tool the
 * result was generated with, ID and name of the result as well as its
 * AtomContainer, the score from the tool, its 3-decimal short version and
 * a BitSet generated by a Fingerprint to make use of similarity comparisons.
 */
public class Result {

	/** The name of the tool this result was generated with. */
	protected String port;
	
	/** The ID of this result within the tool's result set. */
	protected String id;
	
	/** The name of the result, e.g. compound name. */
	protected String name;
	
	/** The AtomContainer representing the molecular structure as provided
	 * by SMILES, InChI or mol/sdf data. */
	protected IAtomContainer mol;
	
	/** The result score for this result generated by the tool. */
	protected double score;
	
	/** The short version of this score, uses 3 decimal digits. */
	protected double scoreShort;
	
	/** The bitset generated by a Fingerprinter for this molecule. */
	protected BitSet bitset;
	
	/** The tied Rank of this result. */
	protected int tiedRank;
	
	/** The URL that leads to this record. */
	protected String url;
	
	/** Relative path to the stored png image. */
	protected String imagePath;
	
	/** The generated link for the MetFrag landing page providing MetFrag with information on this single compound for explicit processing. */
	protected String landingURL;
	
	/** The exact mass of the compound. Either taken from record or computed from IAtomcontainer. */
	protected double exactMass;
	
	/** The sum formula of the compound. Either taken from record or computed from IAtomcontainer. */
	protected String sumFormula;
	
	
	public Result() {
		
	}
	
	/**
	 * Instantiates a new result.
	 * 
	 * @param port the portname, usually name of the tool or application
	 * @param id the id of the result, often KEGG, PubChem or MassBank ID's
	 * @param name the name of the result, e.g. compound name
	 * @param score the score of the result generated by the program
	 * @param mol the mol
	 */
	public Result(String port, String id, String name, double score, IAtomContainer mol) {
		this.port = port;
		this.id = id;
		this.name = name;
		this.score = score;
		this.mol = mol;
		this.setScoreShort(roundThreeDecimals(score));	// set the short version of the score
		this.tiedRank = 1;
		calculateBitSet();			// calculate the bitset for the AtomContainer
	}
	
	/**
	 * Instantiates a new result.
	 * 
	 * @param port the portname, usually name of the tool or application
	 * @param id the id of the result, often KEGG, PubChem or MassBank ID's
	 * @param name the name of the result, e.g. compound name
	 * @param score the score of the result generated by the program
	 * @param mol the mol
	 * @param url the url to the original upstream database entry
	 * @param imagePath the relative path to the stored/created compound image
	 */
	public Result(String port, String id, String name, double score, IAtomContainer mol, String url, String imagePath) {
		this(port, id, name, score, mol);
		//this.setScoreShort(roundThreeDecimals(score));	// set the short version of the score
		this.tiedRank = 1;
		this.setUrl((!url.isEmpty() && url.contains("http") ? url : ""));
		this.imagePath = imagePath;
		//calculateBitSet();			// calculate the bitset for the AtomContainer
	}
	
	/**
	 * Instantiates a new result.
	 * 
	 * @param port the portname, usually name of the tool or application
	 * @param id the id of the result, often KEGG, PubChem or MassBank ID's
	 * @param name the name of the result, e.g. compound name
	 * @param score the score of the result generated by the program
	 * @param mol the mol
	 * @param url the url to the original upstream database entry
	 * @param imagePath the relative path to the stored/created compound image
	 */
	public Result(String port, String id, String name, double score, IAtomContainer mol, String url, String imagePath, 
			String sumFormula, double exactMass) {
		this(port, id, name, score, mol, url, imagePath);
		this.sumFormula = sumFormula;
		this.exactMass = exactMass;
	}
	
	/**
	 * Instantiates a new result.
	 * 
	 * @param port the portname, usually name of the tool or application
	 * @param id the id of the result, often KEGG, PubChem or MassBank ID's
	 * @param name the name of the result, e.g. compound name
	 * @param score the score of the result generated by the program
	 * @param mol the mol
	 * @param url the url to the original upstream database entry
	 * @param imagePath the relative path to the stored/created compound image
	 * @param landingURL the generated link for the MetFrag landing page where this single compound is processed via its upstream DB ID
	 */
	public Result(String port, String id, String name, double score, IAtomContainer mol, String url, String imagePath, String landingURL) {
		this(port, id, name, score, mol, url, imagePath);
		this.landingURL = landingURL;
	}
	
	/**
	 * Instantiates a new result.
	 * 
	 * @param port the portname, usually name of the tool or application
	 * @param id the id of the result, often KEGG, PubChem or MassBank ID's
	 * @param name the name of the result, e.g. compound name
	 * @param score the score of the result generated by the program
	 * @param mol the mol
	 * @param url the url to the original upstream database entry
	 * @param imagePath the relative path to the stored/created compound image
	 * @param landingURL the generated link for the MetFrag landing page where this single compound is processed via its upstream DB ID
	 * @param sumFormula String-representation of the sum formula of this compound
	 * @param exactMass exact mass of the compound as double
	 */
	public Result(String port, String id, String name, double score, IAtomContainer mol, String url, 
			String imagePath, String landingURL, String sumFormula, double exactMass) {
		this(port, id, name, score, mol, url, imagePath, landingURL);
		this.sumFormula = sumFormula;
		this.exactMass = exactMass;
	}
	
	/**
	 * Instantiates a new result.
	 * 
	 * @param port the portname, usually name of the tool or application
	 * @param id the id of the result, often KEGG, PubChem or MassBank ID's
	 * @param name the name of the result, e.g. compound name
	 * @param score the score of the result generated by the program
	 * @param mol the mol
	 * @param tiedRank the tied rank of this result
	 */
	public Result(String port, String id, String name, double score, IAtomContainer mol, int tiedRank) {
		this(port, id, name, score, mol);
		this.tiedRank = tiedRank;
	}
	
	/**
	 * Instantiates a new result.
	 * 
	 */
	public Result(Result r, int tiedRank) {
		this(r.getPort(), r.getId(), r.getName(), r.getScore(), r.getMol(), r.getUrl(), r.getImagePath());
		this.tiedRank = tiedRank;
		this.bitset = r.getBitset();
	}
	
	public Result(ResultExt r) {
		this(r.getPort(), r.getId(), r.getName(), r.getScore(), r.getMol(), r.getUrl(), r.getImagePath());
		this.tiedRank = r.getTiedRank();
		this.sumFormula = r.getSumFormula();
		this.scoreShort = r.getScoreShort();
		this.exactMass = r.getExactMass();
		this.bitset = r.getBitset();
	}
	
	/**
	 * Round an input do score to three decimals.
	 * 
	 * @param d the double value to be rounded
	 * 
	 * @return the resulting three decimal double
	 */
	private double roundThreeDecimals(double d) {
		DecimalFormat threeDForm = new DecimalFormat("#.###");
		return Double.valueOf(threeDForm.format(d));
	}
	
	/**
	 * Calculate the bitset for the AtomContainer.
	 */
	private void calculateBitSet() {
		if(this.mol != null) {
			Fingerprinter fp = new Fingerprinter();	// generate new FingerPrinter
			try {
				this.bitset = fp.getFingerprint(mol);	// generate bitset 
			} catch (CDKException e) {
				System.err.println("Error generating BitSet");
				this.bitset = new BitSet();		// generate new BitSet with all bits set to false
			}
		}
	}
	
	/**
	 * Gets the name of the tool this result was generated from.
	 * 
	 * @return the name of the tool
	 */
	public String getPort() {
		return port;
	}

	/**
	 * Gets the id.
	 * 
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * Gets the name.
	 * 
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the AtomContainer.
	 * 
	 * @return the AtomContainer representing the
	 * molecular structure of a compound
	 */
	public IAtomContainer getMol() {
		return mol;
	}

	/**
	 * Gets the score.
	 * 
	 * @return the score
	 */
	public double getScore() {
		return score;
	}

	/**
	 * Sets the name of the tool this result was generated from.
	 * 
	 * @param port the new name of the tool
	 */
	public void setPort(String port) {
		this.port = port;
	}

	/**
	 * Sets the id.
	 * 
	 * @param id the new id
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * Sets the name.
	 * 
	 * @param name the new name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Sets the AtomContainer.
	 * 
	 * @param mol the new AtomContainer
	 */
	public void setMol(IAtomContainer mol) {
		this.mol = mol;
	}

	/**
	 * Sets the score.
	 * 
	 * @param score the new score
	 */
	public void setScore(double score) {
		this.score = score;
	}

	/**
	 * Sets the bitset.
	 * 
	 * @param bitset the new bitset
	 */
	public void setBitset(BitSet bitset) {
		this.bitset = bitset;
	}

	/**
	 * Gets the bitset.
	 * 
	 * @return the bitset
	 */
	public BitSet getBitset() {
		return bitset;
	}

	/**
	 * Sets the short/rounded version of the score.
	 * 
	 * @param scoreShort the new rounded score
	 */
	public void setScoreShort(double scoreShort) {
		this.scoreShort = scoreShort;
	}

	/**
	 * Gets the rounded three-decimal score.
	 * 
	 * @return the score short
	 */
	public double getScoreShort() {
		return scoreShort;
	}

	public void setTiedRank(int tiedRank) {
		this.tiedRank = tiedRank;
	}

	public int getTiedRank() {
		return tiedRank;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	public void setImagePath(String imagePath) {
		this.imagePath = imagePath;
	}

	public String getImagePath() {
		return imagePath;
	}

	public void setLandingURL(String landingURL) {
		this.landingURL = landingURL;
	}

	public String getLandingURL() {
		return landingURL;
	}

	public double getExactMass() {
		return exactMass;
	}

	public void setExactMass(double exactMass) {
		this.exactMass = exactMass;
	}

	public String getSumFormula() {
		return sumFormula;
	}

	public void setSumFormula(String sumFormula) {
		this.sumFormula = sumFormula;
	}
}
