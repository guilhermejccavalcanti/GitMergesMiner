
class MergeResult {

	MergeCommit commit;
	String projectURL;
	String newMergeCommitSHA;
	
	int ssmergeconfs= 0;
	int ssmergeloc= 0;
	int ssmergerenamingconfs= 0;
	int ssmergedeletionconfs= 0;
	int ssmergeinnerdeletionconfs= 0;
	int ssmergetaeconfs= 0;
	int ssmergenereoconfs= 0;
	int ssmergeinitlblocksconfs= 0;
	int ssmergeacidentalconfs= 0;
	int unmergeconfs= 0;
	int unmergeloc= 0;
	int unmergetime= 0;
	int ssmergetime= 0;
	int unmergeduplicateddeclarationerrors= 0;
	int unmergeorderingconfs= 0;
	int equalconfs= 0;

	public MergeResult(){
	}
}
