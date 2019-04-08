
class ProjecResult {

	String projectName;

	int numberOfScenarios = 0;
	int conflictingScenarios = 0;
	
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
	long unmergetime= 0;
	long ssmergetime= 0;
	int unmergeduplicateddeclarationerrors= 0;
	int unmergeorderingconfs= 0;
	int equalconfs= 0;
	
	int scenariosWithSsmergeConf = 0;
	int scenariosWithTextualConf = 0;

	int scenariosOnlyWithSsmergeConf = 0;
	int scenariosOnlyWithTextualConf = 0;
	
	int scenariosWithRenamingConfs = 0
	int scenariosWithDeletionConfs = 0
	int scenariosWithTaeConfs = 0
	int scenariosWithNereoConfs =0 
	int scenariosWithInitBlocksConfs =0 
	int scenariosWithAcidentalConfs =0
	
	int scenariosWithOrderingConfs =0 
	int scenariosWithDuplicatedDeclarations =0 
	
	public ProjecResult(){
	}
}
