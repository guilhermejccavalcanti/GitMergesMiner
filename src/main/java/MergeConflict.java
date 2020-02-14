/**
 * Class representing a textual merge conflict.
 * 
 * @author Guilherme
 */

public class MergeConflict {

	private String left;
	private String base;
	private String right;
	private String body;

	private int startLOC;
	private int endLOC;

	private String fullyQualifiedMergedClass;

	public static final String MINE_CONFLICT_MARKER = "<<<<<<< MINE";
	public static final String BASE_CONFLICT_MARKER = "||||||| BASE";
	public static final String CHANGE_CONFLICT_MARKER = "=======";
	public static final String YOURS_CONFLICT_MARKER = ">>>>>>> YOURS";

	public MergeConflict(String left, String base, String right, int startLOC, int endLOC) {
		this.left = left;
		this.base = base;
		this.right = right;
		this.startLOC = startLOC;
		this.endLOC = endLOC;
	}
	
	public boolean contains(String leftPattern, String rightPattern) {
		if (leftPattern.isEmpty() || rightPattern.isEmpty()) {
			return false;
		} else {
			leftPattern = (leftPattern.replaceAll("\\r\\n|\\r|\\n", "")).replaceAll("\\s+", "");
			rightPattern = (rightPattern.replaceAll("\\r\\n|\\r|\\n", "")).replaceAll("\\s+", "");
			String lefttrim = (this.left.replaceAll("\\r\\n|\\r|\\n", "")).replaceAll("\\s+", "");
			String righttrim = (this.right.replaceAll("\\r\\n|\\r|\\n", "")).replaceAll("\\s+", "");
			return (lefttrim.contains(leftPattern) && righttrim.contains(rightPattern));
		}
	}

	public String getFullyQualifiedMergedClass() {
		return fullyQualifiedMergedClass;
	}

	public void setFullyQualifiedMergedClass(String fullyQualifiedMergedClass) {
		this.fullyQualifiedMergedClass = fullyQualifiedMergedClass;
	}

	@Override
	public String toString() {
		return this.body;
	}

	/**
	 * @return the LEFT conflicting content
	 */
	public String getLeft() {
		return left;
	}

	/**
	 * @return the BASE conflicting content
	 */
	public String getBase() {
		return base;
	}

	/**
	 * @return the YOURS conflicting content
	 */
	public String getRight() {
		return right;
	}

	/**
	 * @return the startLOC of the conflict
	 */
	public int getStartLOC() {
		return startLOC;
	}
	
	/**
	 * @return the endLOC
	 */
	public int getEndLOC() {
		return endLOC;
	}

	/*
	 * public boolean containsRelaxed(String leftPattern, String rightPattern){
	 * if(leftPattern.isEmpty() || rightPattern.isEmpty()){ return false; } else {
	 * leftPattern =
	 * (leftPattern.replaceAll("\\r\\n|\\r|\\n","")).replaceAll("\\s+","");
	 * rightPattern =
	 * (rightPattern.replaceAll("\\r\\n|\\r|\\n","")).replaceAll("\\s+",""); String
	 * lefttrim = (this.left.replaceAll("\\r\\n|\\r|\\n","")).replaceAll("\\s+","");
	 * String righttrim =
	 * (this.right.replaceAll("\\r\\n|\\r|\\n","")).replaceAll("\\s+","");
	 * 
	 * leftPattern = Util.removeReservedKeywords(leftPattern); rightPattern =
	 * Util.removeReservedKeywords(rightPattern); lefttrim =
	 * Util.removeReservedKeywords(lefttrim); righttrim =
	 * Util.removeReservedKeywords(righttrim);
	 * 
	 * return (lefttrim.contains(leftPattern) && righttrim.contains(rightPattern));
	 * } }
	 */
}