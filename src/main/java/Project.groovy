import java.util.ArrayList;


class Project {
	
	String name
	String url
	String graph
	String miningSinceDate = ""
	String miningUntilDate = ""
	LinkedList<MergeCommit> listMergeCommit
	
	def setMergeCommits(mergeCommits){
		this.listMergeCommit = mergeCommits
	}

}
