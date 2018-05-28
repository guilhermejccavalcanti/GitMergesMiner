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

	private String workingDirectory

	// the url of the repository
	private String remoteUrl

	// the directory to clone in
	private String repositoryDir

	// the work folder
	private String projectsDirectory

	// the temporary folder
	private String tempdir

	// the list of all merge commits
	private ArrayList<MergeCommit> listMergeCommit

	// the referred project
	private Project project

	// the git repository
	private Git git

	// conflicts counter
	private def CONFLICTS

	// signal of error execution, number max of tries 5
	private def ERROR
	private final int NUM_MAX_TRIES = 1

	public Extractor(Project project, boolean withGitMiner){
		//this.projectsDirectory	= "/mnt/e/Mestrado/FPFNAnalysis/projects/"
		//this.projectsDirectory	= "/home/local/CIN/gjcc/fpfnanalysis/projects/"
		//this.projectsDirectory	= "E:/Mestrado/FPFNAnalysis/projects/"
		//this.tempdir			= this.projectsDirectory + "temp/" +this.project.name+"/git"
		//this.tempdir			= "/home/local/CIN/gjcc/fpfnanalysis/temp/" +this.project.name+"/git"

		loadProperties()
		this.project			= project
		this.listMergeCommit 	= this.project.listMergeCommit
		this.remoteUrl 			= this.project.url
		this.tempdir			= this.workingDirectory + "/temp/" + this.project.name+"/git"
		if(new File(this.project.url).exists()){
			this.repositoryDir	= this.project.url
		} else {
			println 'The path informed ' + this.project.url + ' is not a valid git repository'
			System.exit(-1)
		}
		this.CONFLICTS 			= 0
		this.ERROR				= 0
		this.setup()

		//reading projects' merge commits directly from git repository
		if(!withGitMiner){
			MergeCommitsRetriever mergesRetriever = new MergeCommitsRetriever(this.repositoryDir,project.miningSinceDate,project.miningUntilDate)
			ArrayList<MergeCommit> mergeCommits   = mergesRetriever.retrieveMergeCommits()
			project.setMergeCommits(mergeCommits)
			Printer printer = new Printer()
			printer.writeCSV(project.name,mergeCommits)
		}
	}

	public Extractor(MergeCommit mergeCommit){
		//this.projectsDirectory	= "E:/Mestrado/FPFNAnalysis/projects/"
		//this.projectsDirectory	= "/mnt/e/Mestrado/FPFNAnalysis/projects/"
		//this.projectsDirectory	= "/home/local/CIN/gjcc/fpfnanalysis/projects/"
		//this.tempdir			= this.projectsDirectory + "temp/" + mergeCommit.projectName +"/git"
		//this.tempdir			= "/home/local/CIN/gjcc/fpfnanalysis/temp/" + mergeCommit.projectName +"/git"

		loadProperties()
		this.remoteUrl 			= mergeCommit.projectURL
		this.tempdir			= this.workingDirectory + "/temp/" + mergeCommit.projectName +"/git"
		if(new File(mergeCommit.projectURL).exists()){
			this.repositoryDir	= mergeCommit.projectURL
		} else {
			println 'The path informed ' + mergeCommit.projectURL + ' is not a valid git repository'
			System.exit(-1)
		}
		this.CONFLICTS 			= 0
		this.ERROR				= 0
		this.setup()
	}

	public Extractor(){

	}

	def loadProperties(){
		Properties prop = new Properties()
		InputStream input = null

		try {
			//load a properties
			input = new FileInputStream("config.properties")
			prop.load(input)

			this.workingDirectory 	= prop.getProperty("working_directory")
			this.projectsDirectory	= this.workingDirectory +"/projects/"

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

	def extractCommits(){
		def iterationCounter = 1
		Iterator ite = this.listMergeCommit.iterator()
		MergeCommit mc = null
		while(ite.hasNext()){
			if(this.canProceed()){
				mc = (MergeCommit)ite.next()
				println ("Running " + iterationCounter + "/" + this.listMergeCommit.size())
				this.ERROR = 0;
			}

			// the commits to checkout
			def SHA_1 = mc.parent1
			def SHA_2 = mc.parent2
			println ("SHA's [MergeCommit= " + mc.sha 	+ " , Parent1=" + mc.parent1 + " , Parent2=" + mc.parent2 +  "]")


			def ancestorSHA = this.findCommonAncestor(SHA_1, SHA_2)
			if(ancestorSHA != null){
				this.downloadAllFiles(SHA_1, SHA_2, ancestorSHA)
			}else{
				println('commit sha:' + mc.getSha() + ' returned null on common ancestor search.')
			}

			if(this.canProceed()){
				println "----------------------"
				iterationCounter++
			}
		}

		println ("Number of conflicts: " + CONFLICTS)

		def confs = this.project.name + "," + CONFLICTS
		def out = new File('cfts.csv')
		out.append confs
		out.append '\n'
	}

	def extractCommits(Strategy strategy){
		def iterationCounter = 1
		Iterator ite = this.project.listMergeCommit.iterator()
		MergeCommit mergeCommit  = null
		while(ite.hasNext()){
			if(this.canProceed()){
				mergeCommit = (MergeCommit)ite.next()
				println ("Checking " + iterationCounter + "/" + this.project.listMergeCommit.size())
				this.ERROR = 0;
			}

			// the commits to checkout
			def SHA_1 = mergeCommit.parent1
			def SHA_2 = mergeCommit.parent2
			println ("SHA's [MergeCommit= " + mergeCommit.sha 	+ " , Parent1=" + mergeCommit.parent1 + " , Parent2=" + mergeCommit.parent2 +  "]")

			switch(strategy){
				case Strategy.ALL:
					def ancestorSHA = this.findCommonAncestor(SHA_1, SHA_2)
					mergeCommit.ancestor = ancestorSHA;
					if(ancestorSHA != null){
						this.downloadAllFiles(SHA_1, SHA_2, ancestorSHA)
					}else{
						println('commit sha:' + mergeCommit.getSha() + ' returned null on common ancestor search.')
					}
					break

				case Strategy.CONFLICTING:
					this.downloadOnlyConflicting(SHA_1, SHA_2)
					break

				case Strategy.COUNT_CONFLICTS:
					this.countConflicts(SHA_1, SHA_2)
					println ("Number of conflicts: " + CONFLICTS)
					def confs = this.project.name + "," + CONFLICTS
					def out = new File('cfts.csv')
					out.append confs
					out.append '\n'

				default:
					println("NONE mining option selected.");
			}

			if(this.canProceed()){
				println "----------------------"
				iterationCounter++
			}
		}
	}

	def fillAncestors(){
		int i = 0;
		while(i<this.project.listMergeCommit.size()){
			MergeCommit mergeCommit = this.project.listMergeCommit.get(i)
			def SHA_1 = mergeCommit.parent1
			def SHA_2 = mergeCommit.parent2
			def ancestorSHA = this.findCommonAncestor(SHA_1, SHA_2)
			if(ancestorSHA != null){
				mergeCommit.ancestor = ancestorSHA
				mergeCommit.projectName	 = this.project.name
				mergeCommit.projectURL = this.project.url
				mergeCommit.graph	 = this.project.graph
				println ("SHA's [MergeCommit= " + mergeCommit.sha 	+ " , Parent1=" + mergeCommit.parent1 + " , Parent2=" + mergeCommit.parent2 + ", Ancestor=" + mergeCommit.ancestor + "]")
				i++
			} else {
				this.project.listMergeCommit.remove(i);
			}
		}
	}

	def public downloadMergeScenario(MergeCommit mergeCommit) {
		// folder of the revisions being tested
		def allRevFolder = this.projectsDirectory + mergeCommit.projectName + "/revisions/rev_" + mergeCommit.parent1.substring(0, 7) + "_" + mergeCommit.parent2.substring(0, 7)
		try{
			// opening the working directory
			this.git = openRepository();
			// git reset --hard SHA1_1
			this.resetCommand(this.git, mergeCommit.parent1)

			// copy files for parent1 revision
			def destinationDir = allRevFolder + "/rev_left_" + mergeCommit.parent1.substring(0, 7)
			this.copyFiles(this.repositoryDir, destinationDir, "")
			def rec = new RecursiveFileList()
			//rec.removeFiles(new File(destinationDir))

			// git clean -f
			CleanCommand cleanCommandgit = this.git.clean()
			cleanCommandgit.call()

			// git checkout -b new SHA1_2
			def refNew = checkoutAndCreateBranch("new", mergeCommit.parent2)
			// copy files for parent2 revision
			destinationDir = allRevFolder + "/rev_right_" + mergeCommit.parent2.substring(0, 7)
			def excludeDir = "**/" + allRevFolder + "/**"
			this.copyFiles(this.repositoryDir, destinationDir, excludeDir)
			//rec.removeFiles(new File(destinationDir))


			cleanCommandgit = this.git.clean()
			cleanCommandgit.call()
			// git checkout -b ancestor ANCESTOR

			checkoutMasterBranch()
			cleanCommandgit = this.git.clean()
			cleanCommandgit.call()

			def refAncestor = checkoutAndCreateBranch("ancestor", mergeCommit.ancestor)
			// copy files for ancestor revision
			destinationDir = allRevFolder + "/rev_base_" + mergeCommit.ancestor.substring(0, 7)
			excludeDir	   = "**/" + allRevFolder + "/**"
			this.copyFiles(this.repositoryDir, destinationDir, excludeDir)
			//rec.removeFiles(new File(destinationDir))

			def revisionFile 		 = this.writeRevisionsFile(mergeCommit.parent1.substring(0, 7), mergeCommit.parent2.substring(0, 7), mergeCommit.ancestor.substring(0, 7), allRevFolder)
			mergeCommit.revisionFile = revisionFile

			// avoiding references issues
			checkoutMasterBranch()
			this.deleteBranch("ancestor")
			this.deleteBranch("new")

		} catch(org.eclipse.jgit.api.errors.CheckoutConflictException e){
			println "ERROR: " + e
			// reseting
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.api.errors.JGitInternalException f){
			println "ERROR: " + f
			// reseting
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.dircache.InvalidPathException g){
			println "ERROR: " + g
			// reseting
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.api.errors.RefNotFoundException h){
			println "ERROR: " + h
			// reseting
			this.restoreGitRepository()
		} catch(java.lang.NullPointerException i){
			println "ERROR: " + i
			// reseting
			this.restoreGitRepository()
		} finally {
			// closing git repository
			this.git.getRepository().close()
		}
	}

	def private downloadAllFiles(parent1, parent2, ancestor) {
		// folder of the revisions being tested
		def allRevFolder = this.projectsDirectory + this.project.name + "/revisions/rev_" + parent1.substring(0, 7) + "_" + parent2.substring(0, 7)
		try{
			// opening the working directory
			this.git = openRepository();
			// git reset --hard SHA1_1
			this.resetCommand(this.git, parent1)

			// copy files for parent1 revision
			def destinationDir = allRevFolder + "/rev_left_" + parent1.substring(0, 7)
			this.copyFiles(this.repositoryDir, destinationDir, "")
			def rec = new RecursiveFileList()
			rec.removeFiles(new File(destinationDir))

			// git clean -f
			CleanCommand cleanCommandgit = this.git.clean()
			cleanCommandgit.call()

			// git checkout -b new SHA1_2
			def refNew = checkoutAndCreateBranch("new", parent2)
			// copy files for parent2 revision
			destinationDir = allRevFolder + "/rev_right_" + parent2.substring(0, 7)
			def excludeDir = "**/" + allRevFolder + "/**"
			this.copyFiles(this.repositoryDir, destinationDir, excludeDir)
			rec.removeFiles(new File(destinationDir))


			cleanCommandgit = this.git.clean()
			cleanCommandgit.call()
			// git checkout -b ancestor ANCESTOR

			checkoutMasterBranch()
			cleanCommandgit = this.git.clean()
			cleanCommandgit.call()

			def refAncestor = checkoutAndCreateBranch("ancestor", ancestor)
			// copy files for ancestor revision
			destinationDir = allRevFolder + "/rev_base_" + ancestor.substring(0, 7)
			excludeDir	   = "**/" + allRevFolder + "/**"
			this.copyFiles(this.repositoryDir, destinationDir, excludeDir)
			rec.removeFiles(new File(destinationDir))

			this.writeRevisionsFile(parent1.substring(0, 7), parent2.substring(0, 7), ancestor.substring(0, 7), allRevFolder)

			// avoiding references issues
			checkoutMasterBranch()
			this.deleteBranch("ancestor")
			this.deleteBranch("new")

		} catch(org.eclipse.jgit.api.errors.CheckoutConflictException e){
			println "ERROR: " + e
			// reseting
			this.restoreGitRepository()
		}catch(org.eclipse.jgit.api.errors.JGitInternalException f){
			println "ERROR: " + f
			// reseting
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.dircache.InvalidPathException g){
			println "ERROR: " + g
			// reseting
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.api.errors.RefNotFoundException h){
			println "ERROR: " + h
			// reseting
			this.restoreGitRepository()
		} catch(java.lang.NullPointerException i){
			println "ERROR: " + i
			// reseting
			this.restoreGitRepository()
		} finally {
			// closing git repository
			this.git.getRepository().close()
		}
	}

	def private downloadOnlyConflicting(parent1, parent2) {
		// folder of the revisions being tested
		def allRevFolder = this.projectsDirectory + this.project.name + "/revisions/rev_" + parent1.substring(0, 7) + "_" + parent2.substring(0, 7)
		try{
			// opening the working directory
			this.git = openRepository();
			// git reset --hard SHA1_1
			this.resetCommand(this.git, parent1)
			// git clean -f
			CleanCommand cleanCommandgit = this.git.clean()
			cleanCommandgit.call()
			// git checkout -b new SHA1_2
			def refNew = checkoutAndCreateBranch("new", parent2)
			// git checkout master
			checkoutMasterBranch()
			// git merge new
			MergeCommand mergeCommand = this.git.merge()
			mergeCommand.include(refNew)
			MergeResult res = mergeCommand.call()
			if (res.getBase() != null && res.getMergeStatus().equals(MergeResult.MergeStatus.CONFLICTING)){
				println "Revision Base: " + res.getBase().toString()
				println "Conflitcts: " + res.getConflicts().toString()
				def allConflicts = printConflicts(res)
				this.deleteBranch("new")
				this.git.getRepository().close()
				this.moveConflictingFiles(parent1, parent2, allConflicts)
			}
			// avoiding references issues
			this.deleteBranch("new")

			// reseting number of tries
			this.ERROR = 0;

		} catch(org.eclipse.jgit.api.errors.CheckoutConflictException e){
			this.ERROR = this.ERROR+1;
			println "ERROR: " + e
			// reseting
			this.deleteFiles(allRevFolder)
			this.restoreGitRepository()
			println "Trying again..."
		}catch(org.eclipse.jgit.api.errors.JGitInternalException f){
			println "ERROR: " + f
			// reseting
			this.deleteFiles(allRevFolder)
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.dircache.InvalidPathException g){
			println "ERROR: " + g
			// reseting
			this.deleteFiles(allRevFolder)
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.api.errors.RefNotFoundException h){
			println "ERROR: " + h
			// reseting
			this.deleteFiles(allRevFolder)
			this.restoreGitRepository()
		}	catch(java.lang.NullPointerException i){
			println "ERROR: " + i
			// reseting
			this.deleteFiles(allRevFolder)
			this.restoreGitRepository()
		} finally {
			// closing git repository
			this.git.getRepository().close()
		}
	}

	def private countConflicts(parent1, parent2){
		try{
			// opening the working directory
			this.git = openRepository();
			// git reset --hard SHA1_1
			this.resetCommand(this.git, parent1)
			// git clean -f
			CleanCommand cleanCommandgit = this.git.clean()
			cleanCommandgit.call()
			// git checkout -b new SHA1_2
			def refNew = checkoutAndCreateBranch("new", parent2)
			// git checkout master
			checkoutMasterBranch()
			// git merge new
			MergeCommand mergeCommand = this.git.merge()
			mergeCommand.include(refNew)
			MergeResult res = mergeCommand.call()
			if (res.getMergeStatus().equals(MergeResult.MergeStatus.CONFLICTING)){
				println "Revision Base: " + res.getBase().toString()
				println "Conflitcts: " + res.getConflicts().toString()
				printConflicts(res)
				CONFLICTS = CONFLICTS + 1
			}
			// avoiding references issues
			this.deleteBranch("new")

			// reseting number of tries
			this.ERROR = 0;

		} catch(org.eclipse.jgit.api.errors.CheckoutConflictException e){
			this.ERROR = this.ERROR+1;
			println "ERROR: " + e
			// reseting
			this.restoreGitRepository()
			println "Trying again..."
		}catch(org.eclipse.jgit.api.errors.JGitInternalException f){
			println "ERROR: " + f
			// reseting
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.dircache.InvalidPathException g){
			println "ERROR: " + g
			// reseting
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.api.errors.RefNotFoundException h){
			println "ERROR: " + h
			// reseting
			this.restoreGitRepository()
		}	catch(java.lang.NullPointerException i){
			println "ERROR: " + i
			// reseting
			this.restoreGitRepository()
		} finally {
			// closing git repository
			this.git.getRepository().close()
		}
	}

	def private cloneRepository(){
		// prepare a new folder for the cloned repository
		File gitWorkDir = new File(repositoryDir)
		gitWorkDir.mkdirs()

		// then clone
		println "Cloning from " + remoteUrl + " to " + gitWorkDir + "..."
		CloneCommand clone = new CloneCommand()
				.setURI(remoteUrl)
				.setDirectory(gitWorkDir)
		if(remoteUrl.contains('bitbucket')){
			String name = "gjcc@cin.ufpe.br"
			String password = "ganister"

			// credentials
			CredentialsProvider cp = new UsernamePasswordCredentialsProvider(name, password)
			clone.setCredentialsProvider(cp)
		}
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

	def private Git openRepository() {
		try {
			File gitWorkDir = new File(repositoryDir)
			Git git = Git.open(gitWorkDir)
			Repository repository = git.getRepository()
			return git
		} catch(org.eclipse.jgit.errors.RepositoryNotFoundException e){
			println 'The path informed ' + repositoryDir + ' is not a valid git repository'
			e.printStackTrace()
			System.exit(-1)
		}
	}

	def private listAllBranches() {
		List<Ref> refs = this.git.branchList().call()
		for (Ref ref : refs) {
			println "Branch-Before: " + ref + " " + ref.getName() + " " + ref.getObjectId().getName()
		}
	}

	def private checkoutMasterBranch() {
		ChkoutCmd chkcmd = new ChkoutCmd(this.git.getRepository())
		chkcmd.setName("refs/heads/master")
		chkcmd.setForce(true)
		Ref checkoutResult = chkcmd.call()
		println "Checked out branch sucessfully: " + checkoutResult.getName()
	}

	def private Ref checkoutAndCreateBranch(String branchName, String commit){
		ChkoutCmd chkcmd = new ChkoutCmd(this.git.getRepository())
		chkcmd.setName(branchName)
		chkcmd.setStartPoint(commit)
		chkcmd.setCreateBranch(true)
		chkcmd.setForce(true);
		Ref checkoutResult = chkcmd.call()
		println "Checked out and created branch sucessfully: " + checkoutResult.getName()

		return checkoutResult
	}

	def private deleteBranch(String branchName) {
		this.git.branchDelete()
				.setBranchNames(branchName)
				.setForce(true)
				.call()
	}

	def private resetCommand(git, ref){
		ResetCommand resetCommand = git.reset()
		resetCommand.setMode(ResetType.HARD)
		resetCommand.setRef(ref)
		Ref resetResult = resetCommand.call()
		println "Reseted sucessfully to: " + resetResult.getName()
	}

	def private moveConflictingFiles(parent1, parent2, allConflicts) throws org.eclipse.jgit.api.errors.CheckoutConflictException,
			org.eclipse.jgit.api.errors.JGitInternalException,
			org.eclipse.jgit.dircache.InvalidPathException,
			org.eclipse.jgit.api.errors.RefNotFoundException,
	java.lang.NullPointerException  {

		// folder of the revisions being tested
		def allRevFolder = this.projectsDirectory + this.project.name + "/revisions/rev_" + parent1.substring(0, 7) + "_" + parent2.substring(0, 7)
		//try{
		// opening the working directory
		this.git = openRepository();
		// git reset --hard SHA1_1
		this.resetCommand(this.git, parent1)
		// copy files for parent1 revision
		def destinationDir = allRevFolder + "/rev_left_" + parent1.substring(0, 7)
		this.copyFiles(this.repositoryDir, destinationDir, allConflicts)
		// git clean -f
		CleanCommand cleanCommandgit = this.git.clean()
		cleanCommandgit.call()
		// git checkout -b new SHA1_2
		def refNew = checkoutAndCreateBranch("new", parent2)
		// copy files for parent2 revision
		destinationDir = allRevFolder + "/rev_right_" + parent2.substring(0, 7)
		this.copyFiles(this.repositoryDir, destinationDir, allConflicts)
		// git checkout master
		checkoutMasterBranch()
		// git merge new
		MergeCommand mergeCommand = this.git.merge()
		mergeCommand.include(refNew)
		MergeResult res = mergeCommand.call()
		if (res.getMergeStatus().equals(MergeResult.MergeStatus.CONFLICTING)){
			// git reset --hard BASE
			def revBase = (res.getBase().toString()).split()[1]
			this.resetCommand(this.git, revBase)
			// copy files for base revision
			destinationDir = allRevFolder + "/rev_base_" + revBase.substring(0, 7)
			this.copyFiles(this.repositoryDir, destinationDir, allConflicts)
			// the input revisions listed in a file
			this.writeRevisionsFile(parent1.substring(0, 7), parent2.substring(0, 7), revBase.substring(0, 7), allRevFolder)
		}
		// avoiding references issues
		this.deleteBranch("new")

		// reseting number of tries
		this.ERROR = 0;
	}

	def private printConflicts(MergeResult res) {
		Map allConflicts = res.getConflicts();
		def listConflicts = []
		for (String path : allConflicts?.keySet()) {
			int[][] c = allConflicts.get(path);
			println "Conflicts in file " + path
			for (int i = 0; i < c.length; ++i) {
				println " Conflict #" + i
				for (int j = 0; j < (c[i].length) - 1; ++j) {
					if (c[i][j] >= 0)
						println" Chunk for " + res.getMergedCommits()[j] + " starts on line #" + c[i][j];
				}
			}
			listConflicts.add(path)
		}
		return listConflicts
	}

	def private copyFiles(String sourceDir, String destinationDir, String excludeDir){
		new AntBuilder().copy(todir: destinationDir) {
			fileset(dir: sourceDir){
				exclude(name:excludeDir)
			}
		}
	}

	def private copyFiles(String sourceDir, String destinationDir, ArrayList<String> listConflicts){
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
						fileset(dir: sourceDir){
							include(name:file)
						}
					}
				}
			}
		}
	}

	def private boolean canCopy(String fileName){
		boolean can = false
		//		if(fileName.equalsIgnoreCase("java") || fileName.equalsIgnoreCase("py") || fileName.equalsIgnoreCase("cs")){
		//			can = true
		//		}
		//return can
		return true
	}

	def private deleteFiles(String dir){
		(new AntBuilder()).delete(dir:dir,failonerror:false)
	}

	def private String writeRevisionsFile(String leftRev, String rightRev, String baseRev, String dir){
		try{
			def filePath = dir + "/rev_" + leftRev + "-" + rightRev + ".revisions"
			def out = new File(filePath)
			// deleting old files if it exists
			out.delete()
			out = new File(filePath)
			def row = "rev_left_" + leftRev
			out.append row
			out.append '\n'
			row = "rev_base_" + baseRev
			out.append row
			out.append '\n'
			row = "rev_right_" + rightRev
			out.append row
			out.append '\n'
			return filePath
		}catch(Exception e){return ""} //The file is not created, and just return
	}

	def private setup(){
		//keeping a backup dir
		this.openRepository()
		if(!(new File(this.tempdir)).exists()){
			println "Setupping..."
			new AntBuilder().copy(todir:this.tempdir) {fileset(dir:this.repositoryDir, defaultExcludes: false){}}
			println "----------------------"
		}
	}

	def private restoreGitRepository(){
		println "Restoring Git repository " + this.remoteUrl +"..."
		new AntBuilder().delete(dir:this.repositoryDir,failonerror:false)
		new AntBuilder().copy(todir:this.repositoryDir) {fileset(dir:this.tempdir , defaultExcludes: false){}}
	}

	def public restoreWorkingFolder(){
		println "Restoring Git repository " + this.remoteUrl +"..."
		String workingFolder = (new File(this.repositoryDir)).getParent()
		new AntBuilder().delete(dir:workingFolder,failonerror:false)
		new AntBuilder().copy(todir:this.repositoryDir) {fileset(dir:this.tempdir , defaultExcludes: false){}}
	}

	def private canProceed(){
		return this.ERROR == 0 || this.ERROR == NUM_MAX_TRIES
	}

	def private renameMainBranchIfNeeded(Repository repository){
		def branchName = repository.getBranch();
		if(branchName != "master"){
			RenameBranchCommand renameCommand = new RenameBranchCommand(repository);
			renameCommand.setNewName("master")
			renameCommand.call()
		}
	}

	def private String findCommonAncestor(parent1, parent2){

		String ancestor = null
		this.git = openRepository()

		RevWalk walk = new RevWalk(this.git.getRepository())
		walk.setRetainBody(false)
		walk.setRevFilter(RevFilter.MERGE_BASE)
		walk.reset()

		//		ObjectId shaParent1 = ObjectId.fromString(parent1)
		//		ObjectId shaParent2 = ObjectId.fromString(parent2)

		Repository repo 	= this.git.getRepository()
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

		this.git.getRepository().close()
		return ancestor
	}

	def public git_merge(MergeCommit m){
		def command = null

		println('Reseting to base: ' + m.ancestor)
		command = new ProcessBuilder('git','reset','--hard', m.ancestor)
				.directory(new File(this.repositoryDir))
				.redirectErrorStream(true).start()
		command.inputStream.eachLine {println it}
		command.waitFor();

		println('Merging left into base: ' + m.parent1)
		command = new ProcessBuilder('git','merge', m.parent1)
				.directory(new File(this.repositoryDir))
				.redirectErrorStream(true).start()
		command.inputStream.eachLine {println it}
		command.waitFor();

		println('Merging right into base: ' + m.parent2)
		command = new ProcessBuilder('git','merge', m.parent2)
				.directory(new File(this.repositoryDir))
				.redirectErrorStream(true).start()
		command.inputStream.eachLine {println it}
		command.waitFor();

	}

	def public backupRepository(Project p){
		println "Backup Git repository " + this.remoteUrl +"..."
		def local = this.workingDirectory + 'repositories' + File.separator + p.name
		new AntBuilder().delete(dir:local,failonerror:false)
		new AntBuilder().copy(todir:local) {fileset(dir:this.repositoryDir , defaultExcludes: false){}}
		p.url = local
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
		this.projectsDirectory	= "/home/local/CIN/gjcc/fpfnanalysis/projects/"
		this.tempdir			= "/home/local/CIN/gjcc/fpfnanalysis/temp/" + projectname +"/git"
		this.repositoryDir		= this.projectsDirectory + projectname + "/git"

		String basecommit = null
		File gitWorkDir = new File(this.repositoryDir)
		Git git = Git.open(gitWorkDir)
		Repository repository = git.getRepository()
		this.renameMainBranchIfNeeded(repository)

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
	def public String downloadMergeScenario(mergecommit, leftcommit, rightcommit, basecommit, projectname) {
		this.projectsDirectory	= "/home/local/CIN/gjcc/fpfnanalysis/projects/"
		this.tempdir			= "/home/local/CIN/gjcc/fpfnanalysis/temp/" + projectname +"/git"
		this.repositoryDir		= this.projectsDirectory + projectname + "/git"

		String revisionFile 	= null;

		// folder of the revisions being tested
		def allRevFolder = this.projectsDirectory + projectname + "/revisions/rev_" + leftcommit.substring(0, 7) + "_" + rightcommit.substring(0, 7)
		try{
			// opening the working directory
			this.git = openRepository();
			// git reset --hard SHA1_1
			this.resetCommand(this.git,leftcommit)

			// copy files for parent1 revision
			def destinationDir = allRevFolder + "/rev_left_" + leftcommit.substring(0, 7)
			this.copyFiles(this.repositoryDir, destinationDir, "")
			def rec = new RecursiveFileList()
			//rec.removeFiles(new File(destinationDir))

			// git clean -f
			CleanCommand cleanCommandgit = this.git.clean()
			cleanCommandgit.call()

			// git checkout -b new SHA1_2
			def refNew = checkoutAndCreateBranch("new", rightcommit)
			// copy files for parent2 revision
			destinationDir = allRevFolder + "/rev_right_" + rightcommit.substring(0, 7)
			def excludeDir = "**/" + allRevFolder + "/**"
			this.copyFiles(this.repositoryDir, destinationDir, excludeDir)
			//rec.removeFiles(new File(destinationDir))


			cleanCommandgit = this.git.clean()
			cleanCommandgit.call()
			// git checkout -b ancestor ANCESTOR

			checkoutMasterBranch()
			cleanCommandgit = this.git.clean()
			cleanCommandgit.call()

			def refAncestor = checkoutAndCreateBranch("ancestor", basecommit)
			// copy files for ancestor revision
			destinationDir = allRevFolder + "/rev_base_" + basecommit.substring(0, 7)
			excludeDir	   = "**/" + allRevFolder + "/**"
			this.copyFiles(this.repositoryDir, destinationDir, excludeDir)
			//rec.removeFiles(new File(destinationDir))

			revisionFile  = this.writeRevisionsFile(leftcommit.substring(0, 7), rightcommit.substring(0, 7), basecommit.substring(0, 7), allRevFolder)

			// avoiding references issues
			checkoutMasterBranch()
			this.deleteBranch("ancestor")
			this.deleteBranch("new")

			return revisionFile;

		} catch(org.eclipse.jgit.api.errors.CheckoutConflictException e){
			println "ERROR: " + e
			// reseting
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.api.errors.JGitInternalException f){
			println "ERROR: " + f
			// reseting
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.dircache.InvalidPathException g){
			println "ERROR: " + g
			// reseting
			this.restoreGitRepository()
		} catch(org.eclipse.jgit.api.errors.RefNotFoundException h){
			println "ERROR: " + h
			// reseting
			this.restoreGitRepository()
		} catch(java.lang.NullPointerException i){
			println "ERROR: " + i
			// reseting
			this.restoreGitRepository()
		} finally {
			// closing git repository
			this.git.getRepository().close()
			return revisionFile;
		}
	}

}
