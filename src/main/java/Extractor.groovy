import java.text.BreakIterator;
import java.util.ArrayList;

import org.eclipse.jgit.api.CleanCommand
import org.eclipse.jgit.api.CloneCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.RenameBranchCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

import util.ChkoutCmd
import util.RecursiveFileList


class Extractor {

	static String workingDirectory

	// the url of the repository
	static String remoteUrl

	// the directory to clone in
	static String repositoryDir

	// the work folder
	static String projectsDirectory

	// the temporary folder
	static String tempdir

	// the list of all merge commits
	static ArrayList<MergeCommit> listMergeCommit

	// the referred project
	static Project project

	// the git repository
	static Git git

	// conflicts counter
	private static def CONFLICTS

	// signal of error execution, number max of tries 5
	private static def ERROR
	private final int NUM_MAX_TRIES = 1

	private boolean isTravis = false


	def private static loadProperties(){
		Properties prop = new Properties()
		InputStream input = null
	
		try {
			//load a properties
			input = new FileInputStream("config.properties")
			prop.load(input)
	
			workingDirectory 	= prop.getProperty("working_directory")
			projectsDirectory	= workingDirectory +"/projects/"
	
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

	def static private cloneRepository(){
		// prepare a new folder for the cloned repository
		File gitWorkDir = new File(repositoryDir)
		gitWorkDir.mkdirs()
	
		// then clone
		println "Cloning from " + remoteUrl + " to " + gitWorkDir + "..."
		CloneCommand clone = new CloneCommand()
				.setURI(remoteUrl)
				.setDirectory(gitWorkDir)
		//if(remoteUrl.contains('bitbucket')){
		String name = "gjcc@cin.ufpe.br"
		String password = "ganister"
	
		// credentials
		CredentialsProvider cp = new UsernamePasswordCredentialsProvider(name, password)
		clone.setCredentialsProvider(cp)
		//}
		clone.call()
	
		/*		Git.cloneRepository()
		 .setURI(remoteUrl)
		 .setDirectory(gitWorkDir)
		 .call();*/
	
		// now open the created repository
		FileRepositoryBuilder builder = new FileRepositoryBuilder()
		Repository repository = builder.setGitDir(gitWorkDir)
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build();
	
		println "Having repository: " + repository.getDirectory()
		repository.close()
	
	}

	def static private Git openRepository() {
		try {
			File gitWorkDir = new File(repositoryDir)
			Git git = Git.open(gitWorkDir)
			Repository repository = git.getRepository()
			renameMainBranchIfNeeded(repository)
			return git
		} catch(org.eclipse.jgit.errors.RepositoryNotFoundException e){
			cloneRepository()
			openRepository()
		}
	}

	def static private copyFiles(String sourceDir, String destinationDir, String excludeDir){
		new AntBuilder().copy(todir: destinationDir) {
			fileset(dir: sourceDir){ exclude(name:excludeDir) }
		}
	}

	def static private copyFiles(String sourceDir, String destinationDir, ArrayList<String> listConflicts){
		AntBuilder ant = new AntBuilder()
		listConflicts.each {
			def folder = it.split("/")
			def fileName = folder[(folder.size()-1)]
			if(fileName.contains(".")){
				def fileNameSplitted = fileName.split("\\.")
				def fileExt = fileName.split("\\.")[fileNameSplitted.size() -1]
				if(canCopy(fileExt)){
					folder = destinationDir + "/" + (Arrays.copyOfRange(folder, 0, folder.size()-1)).join("/")
					String file = "**/" + it
					ant.mkdir(dir:folder)
					ant.copy(todir: destinationDir) {
						fileset(dir: sourceDir){ include(name:file) }
					}
				}
			}
		}
	}

	def static private deleteFiles(String dir){
		(new AntBuilder()).delete(dir:dir,failonerror:false)
	}

	def static private setup(){
		//keeping a backup dir
		openRepository()
		if(!(new File(tempdir)).exists()){
			println "Setupping..."
			new AntBuilder().copy(todir:tempdir) {fileset(dir:repositoryDir, defaultExcludes: false){}}
			println "----------------------"
		}
	}

	def private restoreGitRepository(){
		println "Restoring Git repository " + remoteUrl +"..."
		new AntBuilder().delete(dir:repositoryDir,failonerror:false)
		new AntBuilder().copy(todir:repositoryDir) {fileset(dir:tempdir , defaultExcludes: false){}}
	}

	def static private renameMainBranchIfNeeded(Repository repository){
		def branchName = repository.getBranch();
		if(branchName != "master"){
			RenameBranchCommand renameCommand = new RenameBranchCommand(repository);
			renameCommand.setNewName("master")
			renameCommand.call()
		}
	}

	def static private String findCommonAncestor(parent1, parent2){
	
		String ancestor = null
		git = openRepository()
	
		RevWalk walk = new RevWalk(git.getRepository())
		walk.setRetainBody(false)
		walk.setRevFilter(RevFilter.MERGE_BASE)
		walk.reset()
	
		//		ObjectId shaParent1 = ObjectId.fromString(parent1)
		//		ObjectId shaParent2 = ObjectId.fromString(parent2)
	
		Repository repo 	= git.getRepository()
		ObjectId shaParent1 = repo.resolve(parent1)
		ObjectId shaParent2 = repo.resolve(parent2)
	
		ObjectId commonAncestor = null
	
		try {
			walk.markStart(walk.parseCommit(shaParent1))
			walk.markStart(walk.parseCommit(shaParent2))
			commonAncestor = walk.next()
		} catch (Exception e) {
			println ('WARNING: ' + e.getMessage())
			//e.printStackTrace()
		}
	
		if(commonAncestor != null){
			ancestor = commonAncestor.toString()substring(7, 47)
			println('The common ancestor is: ' + ancestor)
		}
	
		git.getRepository().close()
		return ancestor
	}

	public static init(Project p, boolean withGitMiner){
		loadProperties()
		project	= p
		listMergeCommit = project.listMergeCommit
		remoteUrl = project.url
		tempdir	= workingDirectory + "/temp/" + project.name.replace("/","_")+"/git"
		repositoryDir = projectsDirectory + project.name.replace("/","_") + "/git"

		if(App.isMine_local()){
			if(new File(project.url).exists()){
				repositoryDir	= project.url
			} else {
				println 'The path informed ' + project.url + ' is not a valid git repository'
				System.exit(-1)
			}
		}

		CONFLICTS 			= 0
		ERROR				= 0
		setup()

		//reading projects' merge commits directly from git repository
		if(!withGitMiner){
			MergeCommitsRetriever mergesRetriever = new MergeCommitsRetriever(repositoryDir,project.miningSinceDate,project.miningUntilDate)
			ArrayList<MergeCommit> mergeCommits   = mergesRetriever.retrieveMergeCommits()
			project.setMergeCommits(mergeCommits)
			Printer printer = new Printer()
			printer.writeCSV(project.name.replace("/","_"),mergeCommits)
		}
	}

	/*
	 public Extractor(Project project, boolean withGitMiner){
	 loadProperties()
	 project			= project
	 listMergeCommit 	= project.listMergeCommit
	 remoteUrl 			= project.url
	 tempdir			= workingDirectory + "/temp/" + project.name.replace("/","_")+"/git"
	 repositoryDir		= projectsDirectory + project.name.replace("/","_") + "/git"
	 if(App.isMine_local()){
	 if(new File(project.url).exists()){
	 repositoryDir	= project.url
	 } else {
	 println 'The path informed ' + project.url + ' is not a valid git repository'
	 System.exit(-1)
	 }
	 }
	 CONFLICTS 			= 0
	 ERROR				= 0
	 setup()
	 //reading projects' merge commits directly from git repository
	 if(!withGitMiner){
	 MergeCommitsRetriever mergesRetriever = new MergeCommitsRetriever(repositoryDir,project.miningSinceDate,project.miningUntilDate)
	 ArrayList<MergeCommit> mergeCommits   = mergesRetriever.retrieveMergeCommits()
	 project.setMergeCommits(mergeCommits)
	 Printer printer = new Printer()
	 printer.writeCSV(project.name.replace("/","_"),mergeCommits)
	 }
	 }
	 public Extractor(MergeCommit mergeCommit){
	 loadProperties()
	 remoteUrl 			= mergeCommit.projectURL
	 tempdir			= workingDirectory + "/temp/" + mergeCommit.projectName.replace("/","_") +"/git"
	 repositoryDir		= projectsDirectory + mergeCommit.projectName.replace("/","_") + "/git"
	 if(App.isMine_local()){
	 if(new File(mergeCommit.projectURL).exists()){
	 repositoryDir	= project.url
	 } else {
	 println 'The path informed ' + project.url + ' is not a valid git repository'
	 System.exit(-1)
	 }
	 }
	 CONFLICTS 			= 0
	 ERROR				= 0
	 setup()
	 }
	 public Extractor(){
	 }
	 */
	
	def static fillAncestors(){
		int i = 0;
		while(i<project.listMergeCommit.size()){
			MergeCommit mergeCommit = project.listMergeCommit.get(i)
			def SHA_1 = mergeCommit.parent1
			def SHA_2 = mergeCommit.parent2
			def ancestorSHA = findCommonAncestor(SHA_1, SHA_2)
			if(ancestorSHA != null){
				mergeCommit.project = project
				mergeCommit.ancestor = ancestorSHA
				mergeCommit.projectName	 = project.name
				mergeCommit.projectURL = project.url
				mergeCommit.graph	 = project.graph
				println ("SHA's [MergeCommit= " + mergeCommit.sha 	+ " , Parent1=" + mergeCommit.parent1 + " , Parent2=" + mergeCommit.parent2 + ", Ancestor=" + mergeCommit.ancestor + "]")
				i++
			} else {
				project.listMergeCommit.remove(i);
			}
		}
	}

	def public static restoreWorkingFolder(){
		println "Restoring Git repository " + remoteUrl +"..."
		String workingFolder = (new File(repositoryDir))
		new AntBuilder().delete(dir:workingFolder,failonerror:false)
		new AntBuilder().copy(todir:repositoryDir) {fileset(dir:tempdir , defaultExcludes: false){}}
	}

	def public static backupRepository(Project p){
		println "Backup Git repository " + remoteUrl +"..."
		def local = workingDirectory + 'repositories' + File.separator + p.name
		new AntBuilder().delete(dir:local,failonerror:false)
		new AntBuilder().copy(todir:local) {fileset(dir:repositoryDir , defaultExcludes: false){}}
		p.url = local
	}

	def public static git_merge(MergeCommit m){
		def command = null

		println('Reseting to base: ' + m.ancestor)
		command = new ProcessBuilder('git','reset','--hard', m.ancestor)
				.directory(new File(repositoryDir))
				.redirectErrorStream(true).start()
		command.inputStream.eachLine {println it}
		command.waitFor();

		println('Merging left into base: ' + m.parent1)
		command = new ProcessBuilder('git','merge', m.parent1)
				.directory(new File(repositoryDir))
				.redirectErrorStream(true).start()
		command.inputStream.eachLine {println it}
		command.waitFor();

		println('Merging right into base: ' + m.parent2)
		command = new ProcessBuilder('git','merge', m.parent2)
				.directory(new File(repositoryDir))
				.redirectErrorStream(true).start()
		command.inputStream.eachLine {println it}
		command.waitFor();
	}

	def public static git_diff(MergeCommit m){
		def command = null

		println('diff left to base...')
		command = new ProcessBuilder('git','diff','--name-only', m.parent1 ,m.ancestor)
				.directory(new File(repositoryDir))
				.redirectErrorStream(true).start()
		command.inputStream.eachLine {
			if(it.toLowerCase().contains(".java")){
				m.changedLeftFiles.add(it);
			}
		}
		command.waitFor();

		println('diff right to base... ')
		List<String> changedRightFiles = new ArrayList<String>()
		command = new ProcessBuilder('git','diff','--name-only', m.parent2 ,m.ancestor)
				.directory(new File(repositoryDir))
				.redirectErrorStream(true).start()
		command.inputStream.eachLine {
			if(it.toLowerCase().contains(".java")){
				m.changedRightFiles.add(it);
			}
		}
		command.waitFor();
	}

	def public String git_push(){
		try{
			def command = null
			String newMergeCommitSha = ""
			println('Push resulting merge result...')

			//add files to commit
			command = new ProcessBuilder('git','add','.').directory(new File(repositoryDir)).redirectErrorStream(true).start()
			command.inputStream.eachLine {println it}
			command.waitFor();

			//commiting
			command = new ProcessBuilder('git','commit','-m','\"replay merge commit\"').directory(new File(repositoryDir)).redirectErrorStream(true).start()
			command.inputStream.eachLine {println it}
			command.waitFor();

			//pushing
			command = new ProcessBuilder('git','push','origin','master','--force').directory(new File(repositoryDir)).redirectErrorStream(true).start()
			command.inputStream.eachLine {
				println it
				newMergeCommitSha += it
			}
			command.waitFor();

			try{
				newMergeCommitSha = newMergeCommitSha.split("\\.\\.\\.")[1].split(" ")[0]
			}catch(Exception e){
				newMergeCommitSha = newMergeCommitSha.split("\\.\\.")[1].split(" ")[0]
			}
			return newMergeCommitSha
		} catch(Exception e){
			e.printStackTrace()
		}
		return null
	}

	def static revertMergedLog(File log){
		boolean fillSemistructuredMergeOutput = false;
		boolean fillStructuredMergeOutput = false;
		boolean fillLeftContent = false;
		boolean fillBaseContent = false;
		boolean fillRightContent = false;

		List<MergedLog> mls = new ArrayList();
		MergedLog mL = new MergedLog();

		log.eachLine { String line ->
			try{
				if(line.contains("########################################################")){
					fillRightContent = false
				} else if(line.contains("Files:")){
					mls.add(mL);
					String[] columns = (line.split(':')[1]).split(',');

					mL = new MergedLog();
					mL.projectName = columns[0];
					mL.mergeCommit = columns[1];
					mL.mergedFile  = (new File(columns[2].replaceAll('\\.', '/'))).getName();
				} else if(line.contains('Semistructured Merge Output:')){
					fillSemistructuredMergeOutput = true;

				} else if(line.contains('Structured Merge Output:')){
					fillSemistructuredMergeOutput = false;
					fillStructuredMergeOutput = true;

				} else if(line.contains('Left Content:')){
					fillStructuredMergeOutput = false;
					fillLeftContent = true;

				} else if(line.contains('Base Content:')){
					fillLeftContent = false;
					fillBaseContent = true;

				} else if(line.contains('Right Content:')){
					fillBaseContent = false;
					fillRightContent = true;
				} else {
					if(fillSemistructuredMergeOutput) {
						mL.SemistructuredMergeOutput += line + '\n';
					} else if(fillStructuredMergeOutput){
						mL.StructuredMergeOutput += line + '\n';
					} else if(fillLeftContent){
						mL.LeftContent += line + '\n';
					} else if(fillBaseContent){
						mL.BaseContent += line + '\n';
					} else if(fillRightContent){
						mL.RightContent += line + '\n';
					} else {
						//Skip line
					}
				}
			} catch(Exception e){
				//Skip line
			}
		}
		return mls;
	}

	static void main (String[] args){
		//		//testing
		//		MergeCommit mc = new MergeCommit()
		//		mc.sha 		= "b4df7ee0b908f16cce2f7c819927fe5deb8cb6b9"
		//		mc.parent1  = "fd21ef43df591ef86ad899d96d2d6a821ebb342d"
		//		mc.parent2  = "576c6b3966cb85353ba874f6c9f2e65c4a89c70b"
		//
		//		ArrayList<MergeCommit> lm = new ArrayList<MergeCommit>()
		//		lm.add(mc)
		//
		//		Project p = new Project()
		//		p.name = "rgms"
		//		p.url = "https://github.com/spgroup/rgms.git"
		//		p.graph = "C:/Users/Guilherme/Documents/workspace/gitminer-master/gitminer-master/graph.db_30-10"
		//		p.listMergeCommit = lm
		//
		//		Extractor ex = new Extractor(p)
		//		//ex.extractCommits()

		//new AntBuilder().copy(todir:"C:/GGTS/ggts-bundle/workspace/others/git clones bkp") {fileset(dir:"C:/GGTS/ggts-bundle/workspace/others/git clones" , defaultExcludes: false){}}
		//new AntBuilder().copy(todir:"C:/Vbox/examples_esem") {fileset(dir:"C:/Vbox/FSTMerge/examples" , defaultExcludes: false){}}}
		//new AntBuilder().zip(destfile: "C:\\Users\\Guilherme\\.m2.zip", basedir: "C:\\Users\\Guilherme\\.m2")
		new AntBuilder().copy(todir:"/home/local/CIN/gjcc/fpfnanalysis/samplerpl") {fileset(dir:"/home/local/CIN/gjcc/fpfnanalysis/samplerpl_bkp" , defaultExcludes: false){}}}

	//		String memberName = "first";
	//		String s = "return first() + familyName;"
	//		println s.matches("(?s).*\\b"+memberName+"\\b.*")

	//}

	//standalone functions
	def public String findBaseCommit(parent1, parent2, projectname){
		projectsDirectory	= "/home/local/CIN/gjcc/fpfnanalysis/projects/"
		tempdir			= "/home/local/CIN/gjcc/fpfnanalysis/temp/" + projectname +"/git"
		repositoryDir		= projectsDirectory + projectname + "/git"

		String basecommit = null
		File gitWorkDir = new File(repositoryDir)
		Git git = Git.open(gitWorkDir)
		Repository repository = git.getRepository()
		renameMainBranchIfNeeded(repository)

		RevWalk walk = new RevWalk(git.getRepository())
		walk.setRetainBody(false)
		walk.setRevFilter(RevFilter.MERGE_BASE)
		walk.reset()

		Repository repo 	= git.getRepository()
		ObjectId shaParent1 = repo.resolve(parent1)
		ObjectId shaParent2 = repo.resolve(parent2)
		ObjectId commonAncestor = null

		try {
			walk.markStart(walk.parseCommit(shaParent1))
			walk.markStart(walk.parseCommit(shaParent2))
			commonAncestor = walk.next()
		} catch (Exception e) {
			println ('WARNING: ' + e.getMessage())
		}

		if(commonAncestor != null){
			basecommit = commonAncestor.toString()substring(7, 47)
			println('The common ancestor is: ' + basecommit)
		}

		git.getRepository().close()
		return basecommit
	}

}
