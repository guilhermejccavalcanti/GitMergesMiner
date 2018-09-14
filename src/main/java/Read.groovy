import java.util.ArrayList;

class Read {

	private ArrayList<MergeCommit> listMergeCommit
	private ArrayList<Project> listProject
	private String csvCommitsFile
	private String csvProjectsFile
	private String github_user

	public Read(fileName, boolean withGitMiner){
		this.listMergeCommit = new ArrayList<MergeCommit>()
		this.listProject 	 = new ArrayList<Project>()
		this.csvProjectsFile = fileName
		this.readProjectsCSV(withGitMiner)
	}

	def setCsvCommitsFile(fileName){
		this.csvCommitsFile = fileName
	}

	def readCommitsCSV() {
		BufferedReader br = null
		String line 	  = ""
		String csvSplitBy = ","
		try {
			br = new BufferedReader(new FileReader(this.csvCommitsFile))
			br.readLine()
			while ((line = br.readLine()) != null) {
				String[] shas = line.split(csvSplitBy)

				def mergeCommit = new MergeCommit()
				mergeCommit.sha 	= shas[0]
				mergeCommit.parent1 = shas[1]
				mergeCommit.parent2 = shas[2]

				this.listMergeCommit.add(mergeCommit)

				println ("SHA's [MergeCommit= " + mergeCommit.sha 	+ " , Parent1=" + mergeCommit.parent1 + " , Parent2=" + mergeCommit.parent2 +  "]")
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace()
		} catch (IOException e) {
			e.printStackTrace()
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace()
				}
			}
		}
	}

	def readProjectsCSV(boolean withGitMiner) {
		loadProperties()
		BufferedReader br = null
		String line = ""
		String csvSplitBy = ","
		try {
			br = new BufferedReader(new FileReader(this.csvProjectsFile))
			br.readLine()
			while ((line = br.readLine()) != null) {
				String[] info = line.split(csvSplitBy)

				def project 	= new Project()
				project.name 	= this.github_user + "/" + info[0].split("/")[1]
				project.url 	= "https://github.com/"  + this.github_user + "/" +(info[1].split("\\.com/")[1]).split("/")[1]
				project.originalName = info[0]
				project.originalURL  = info[1]

				if(!withGitMiner && info.length == 4) {
					project.miningSinceDate = info[2]
					project.miningUntilDate = info[3]
				}
				if(!withGitMiner && info.length == 3) {
					project.miningUntilDate = info[2]
				}
				if(withGitMiner){
					project.graph 	= info[2]
				}

				if(exists(project)){
					this.listProject.add(project)
					if(withGitMiner){
						println ("PROJECT [Name= " + project.name + " , Url=" + project.url + " , Graph dir=" + project.graph +  "]")
					}else{
						println ("PROJECT [Name= " + project.name + " , Url=" + project.url +  "]")
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace()
		} catch (IOException e) {
			e.printStackTrace()
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace()
				}
			}
		}
	}

	def ArrayList<MergeCommit> getMergeCommitsList(){
		return this.listMergeCommit
	}

	def ArrayList<Project> getProjects(){
		return this.listProject
	}

	//	static void main(args) {
	//		Reader obj = new Reader("commits.csv")
	//		obj.readCSV()
	//	}

	def loadProperties(){
		Properties prop = new Properties()
		InputStream input = null
		try {
			//load a properties
			input = new FileInputStream("config.properties")
			prop.load(input)

			this.github_user = prop.getProperty("github_user")
		} catch (IOException ex) {
			ex.printStackTrace()
			System.exit(-1)
		} finally{
			if(input!=null){
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace()
				}
			}
		}
	}

	def boolean exists(Project p){
		String result = ''
		def command = null
		command = new ProcessBuilder('curl','https://api.github.com/repos/'+p.name)
				.redirectErrorStream(true).start()
		command.inputStream.eachLine {result+=it}
		command.waitFor();
		return !result.toLowerCase().contains('not found')
	}
}
