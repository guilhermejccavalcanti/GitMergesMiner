
public class MergeCommit {

	public String sha
	public String parent1
	public String parent2
	public String ancestor
	public String  projectName
	public String projectURL
	public String graph
	public String revisionFile
	public Date date

	def String toString(){
		return 'SHA= ' + this.sha + ', Parent1= ' +this.parent1+', Parent2= ' +this.parent2+ ', Ancestor= ' +this.ancestor + ', Project= ' +this.projectName + ', Graph= ' +this.graph+ ', File= ' + this.revisionFile

	}

}
