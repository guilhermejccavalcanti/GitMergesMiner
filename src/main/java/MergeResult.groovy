
class MergeResult {
	
	MergeCommit commit;
	String projectURL;
	String newMergeCommitSHA;
	
	int consecutiveLinesonly = 0;
	int spacingonly 	 = 0;
	int samePositiononly = 0;
	int sameStmtonly 	 = 0;
	int otheronly 		 = 0;
	int consecutiveLinesAndSamePosition = 0;
	int consecutiveLinesAndsameStmt 	= 0;
	int otherAndsamePosition 	= 0;
	int otherAndsameStmt 		= 0;
	int spacingAndSamePosition 	= 0;
	int spacingAndSameStmt = 0;
	int ssmergeConf = 0;
	int jdimeConf   = 0;
	int textualConf = 0;
	long ssmergetime = 0L;
	long unmergetime = 0L;
	long jdimetime   = 0L;
	boolean isSsEqualsToUn = true;
	boolean isStEqualsToUn = true;
	boolean sucessfullmerge= true;
	
	String travisStatus 	= "NONE"
	String travisBuildURI 	= null
	String travisJobURI = null
	int travisBuildTime	= 0
	
	int changedFiles = 0;
	int commonChangedFiles = 0;
	int changedMethods = 0;
	int commonChangedMethods = 0;
	
	int ssmergeConfNoCL = 0;
	int ssmergeConfNoWS = 0;
	int ssmergeConfNoCLWS = 0;
	
	public MergeResult(){
	}
}
