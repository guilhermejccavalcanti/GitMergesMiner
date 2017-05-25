import org.codehaus.groovy.antlr.Main;
import com.tinkerpop.blueprints.impls.tg.*
import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph
import com.tinkerpop.gremlin.groovy.Gremlin
import com.tinkerpop.gremlin.groovy.GremlinGroovyPipeline
import com.tinkerpop.gremlin.pipes.transform.PropertyMapPipe;
import com.tinkerpop.gremlin.*
import com.tinkerpop.pipes.util.StartPipe;

import org.neo4j.management.impl.*


class GremlinQuery {

	ArrayList<MergeCommit> mergeCommitsList = new ArrayList<MergeCommit>()
	Graph graph

	public GremlinQuery(String path){
		Gremlin.load()
		this.setAllMergeCommits(path)
	}

	public ArrayList<MergeCommit> getMergeCommitsList(){
		return this.mergeCommitsList
	}

	public void setAllMergeCommits(String path){
		this.setGraph(path)
		def mergeCommitShas = this.getShas()
		for(sha in mergeCommitShas){
			MergeCommit mc = new MergeCommit()
			mc.sha = sha
			try{
				String[] parents = this.getParentsSha(sha)
				mc.parent1 = parents.getAt(0)
				mc.parent2 = parents.getAt(1)

				this.setMergeCommit(mc)
			} catch(ArrayIndexOutOfBoundsException e){
				println(e.getMessage())
			} finally {
				continue;
			}
		}
	}

	public void setGraph(String path){
		this.graph = new Neo4jGraph(path)
	}

	public void setMergeCommit(MergeCommit mc){
		this.mergeCommitsList.add(mc)
	}


	public ArrayList<String> getShas() {
		def mergeCommits = this.graph.V.map.filter{it._type == "COMMIT" & it.isMerge == true}.sort{it.date}
		ArrayList<String> results = new ArrayList<String>()
		for(commit in mergeCommits){
			results.add(this.auxGetSha(commit.toString()))
		}
		return results
	}

	private String auxGetSha(String commit){
		String a = commit.substring(1, commit.length() -1)
		String delims = ","
		String[] tokens = a.split(delims);

		boolean foundSha = false
		int counter = 0
		String s =""
		while((!foundSha) && (counter < tokens.length)){
			s = tokens[counter]
			if(s.contains(" hash:") && s.length() == 46){
				foundSha = true
			}else{
				counter++
			}
		}
		String sha = ""
		if(foundSha){
			sha = s.substring(6)
			println(sha)
		}
		return sha
	}

	public ArrayList<String> getParentsSha(String shaSon){
		def commit = this.graph.idx("commit-idx").get("hash", shaSon).first()
		def parentsTemp = commit.outE('COMMIT_PARENT').sort{it.date}
		ArrayList<String> parentsSha = new ArrayList<String>()

		for(parent in parentsTemp){
			String id = this.auxGetParentsID(parent.toString())
			def parentCommit = this.graph.v(id).map
			for(p in parentCommit){
				parentsSha.add(this.auxGetSha(p.toString()))
			}
		}
		return parentsSha
	}


	private String auxGetParentsID(String parent){
		String delims = "[>]"
		String[] tokens = parent.toString().split(delims)
		String idTemp = tokens[1]
		String id = idTemp.substring(0, (idTemp.size() - 1))
		return id
	}
}
