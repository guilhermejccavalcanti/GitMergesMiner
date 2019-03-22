import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream;
import java.io.PrintWriter
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher

import util.LoggingOutputStream;
import util.LoggerPrintStream;
import util.StdOutErrLevel;
import util.TravisFinder;
import util.Tuple;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

import javax.sql.CommonDataSource;


class App {

	static boolean restore_repositories = true;
	static boolean is_building_jdime  = false
	static boolean run_travis = true;
	static boolean filter_known_builds = true;
	static boolean skip_2nd_run = false;


	public static void main (String[] args){
		//execution configuration parameters
		//		def cli = new CliBuilder()
		//		cli.with {
		//			r longOpt: 'restore', 'do not restore git repositories'
		//			s longOpt: 'jdime', 'attempt to build jdime code'
		//			t longOpt: 'travis', 'do not execute Travis on merged scenario'
		//			b longOpt: 'builds', 'do not filter scenarios by known builds (builds.csv)'
		//			f longOpt: 'faster', 'skip second run (builds of jdime merged code)'
		//		}
		//		def options = cli.parse(args)
		//		if (options.r) {
		//			restore_repositories = false
		//		}
		//		if (options.s) {
		//			is_building_jdime = true
		//		}
		//		if(options.t){
		//			run_travis = false
		//		}
		//		if(options.b){
		//			filter_known_builds = false
		//		}
		//		if(options.f){
		//			skip_2nd_run = true
		//		}
		//		boolean alreadyExecuted1stRun = new File("conflictingJDIME").exists()
		//		boolean changeGitConfig = !alreadyExecuted1stRun
		//		if(alreadyExecuted1stRun)is_building_jdime = true;
		//		//managing execution log
		//		File f = new File(System.getProperty("user.home") + File.separator + ".jfstmerge" + File.separator + 'execution.log')
		//		if(!f.exists()){
		//			f.getParentFile().mkdirs()
		//			f.createNewFile()
		//		}
		//		//configuring git merge driver
		//		File gitconfig = new File(System.getProperty("user.home") + File.separator + ".gitconfig")
		//		if(changeGitConfig){
		//			if(!gitconfig.exists()) {
		//				throw new RuntimeException( 'ERROR: .gitconfig not found on ' + gitconfig.getParent() + '. S3M tool not installed?')
		//			}
		//			String gitconfigContents =  gitconfig.text
		//			if(is_building_jdime){
		//				gitconfig.text = gitconfigContents.replaceAll("-g", "-g -s")
		//			} else {
		//				gitconfig.text = gitconfigContents.replaceAll("-s", "")
		//			}
		//		}
		//		//1st run (attempts to build non-conflicting code of the first tool)
		//		run_gitmerges()
		//		//storing 1st results
		//		def resultsFolder = "conflictingJDIME";
		//		if(is_building_jdime){
		//			resultsFolder = "conflictingS3M"
		//		}
		//		boolean success = f.getParentFile().renameTo(new File(resultsFolder))
		//		boolean alreadyExecuted2ndRun = new File("conflictingS3M").exists()
		//		if(!skip_2nd_run){
		//			if(success && !alreadyExecuted2ndRun){
		//				//configuration for 2nd run
		//				is_building_jdime = !is_building_jdime
		//				restore_repositories = true;
		//				//re-managing execution log
		//				if(f.getParentFile().exists()) f.getParentFile().deleteDir()
		//				f.getParentFile().mkdirs()
		//				f.createNewFile()
		//				//re-configuring git merge driver
		//				if(!gitconfig.exists()) {
		//					throw new RuntimeException( 'ERROR: .gitconfig not found on ' + gitconfig.getParent() + '. S3M tool not installed?')
		//				}
		//				def gitconfigContents =  gitconfig.text
		//				if(is_building_jdime){
		//					gitconfig.text = gitconfigContents.replaceAll("-g", "-g -s")
		//				} else {
		//					gitconfig.text = gitconfigContents.replaceAll("-s", "")
		//				}
		//				//2nd run (attempts to build non-conflicting code of the second tool)
		//				run_gitmerges()
		//				//storing 2nd results
		//				resultsFolder = "conflictingJDIME";
		//				if(is_building_jdime){
		//					resultsFolder = "conflictingS3M"
		//				}
		//				success = f.getParentFile().renameTo(new File(resultsFolder))
		//				if(!success){
		//					throw new RuntimeException( 'ERROR: unable to store 2nd run results')
		//				}
		//			} else {
		//				if(!success){
		//					throw new RuntimeException( 'ERROR: unable to store 1st run results')
		//				}
		//			}
		//		}
		computeProjectStatistics()
		//		computeManualAnalysisFiles()
		println 'DONE!'

	}

	def public static run_gitmerges(){
		Read r = new Read("projects.csv",false)
		def projects = r.getProjects()
		if(restore_repositories){
			restoreGitRepositories(projects)
		}

		//fill merge scenarios info (base,left,right)
		projects.each {
			Extractor e = new Extractor(it,false)
			e.fillAncestors()
			println('Project ' + it.name + " read")
		}

		//reproduce the 'git merge' command for each merge scenario
		LinkedList<MergeCommit> merge_commits = fillMergeCommitsListForHorizontalExecution(projects)
		if(filter_known_builds){
			filterByKnownBuilds(merge_commits)
		}
		for(int i=0; i<merge_commits.size();i++){
			MergeCommit m = merge_commits.get(i);
			println ('Analysing ' + ((i+1)+'/'+merge_commits.size()) + ': ' +  m.sha)
			fillExecutionLog(m)
			Extractor e = new Extractor(m)
			e.git_diff(m)
			e.git_merge(m)

			//compute statistics related to each scenario
			MergeResult result = computeScenarioStatistics(m)

			if(result.sucessfullmerge && e.isTravis && run_travis){
				//travis only necessary when tools differ
				boolean shouldPush = false;
				if(is_building_jdime){
					if(result.ssmergeConf > 0 && result.jdimeConf == 0) {
						shouldPush = true;
					}
				} else {
					if(result.ssmergeConf == 0 && result.jdimeConf>0) {
						shouldPush = true;
					}
				}
				if(shouldPush){
					//push to fork, build fork
					def new_sha = e.git_push()
					if(new_sha != null){
						Tuple<String,String,Integer> travisResult = TravisFinder.findStatus(new_sha, m.parent1, m.ancestor, m.parent2, result.commit.projectName)
						result.travisStatus = travisResult.x
						result.travisBuildURI = travisResult.y
						result.travisBuildTime = travisResult.z
						result.travisJobURI = travisResult.w
						result.newMergeCommitSHA = new_sha
					}
				}
			}
			//log statistics related to each scenario
			logScenarioStatistics(result)
		}
		println 'Analysis Finished!'
	}

	def private static restoreGitRepositories(ArrayList<Project> projects){
		projects.each {
			Extractor e = new Extractor(it,true)
			e.restoreWorkingFolder()
		}
		println('Restore finished!\n')
	}

	def private static LinkedList<MergeCommit> fillMergeCommitsListForHorizontalExecution(ArrayList<Project> projects){
		/*
		 * adds a random factor to the executions's order
		 */
		ArrayList<String> alreadyExecutedSHAs = restoreExecutionLog();
		LinkedList<MergeCommit> horizontalExecutionMergeCommits = new LinkedList<MergeCommit>()
		int aux = projects.size()
		int i 	= 0;
		while(i < projects.size()) {
			Project p = projects.get(i)
			if(!p.listMergeCommit.isEmpty()){
				MergeCommit mergeCommit = p.listMergeCommit.poll()
				if(!alreadyExecutedSHAs.contains(mergeCommit.projectName+','+mergeCommit.sha)){
					//if(alreadyExecutedSHAs.contains(mergeCommit.projectName+','+mergeCommit.sha)){
					horizontalExecutionMergeCommits.add(mergeCommit)
				}
			}
			if(p.listMergeCommit.isEmpty()){
				projects.remove(i)
			}
			aux 	= projects.size()
			if(aux == 0){
				break
			}
			if(i >= (projects.size() - 1)){
				i = 0;
			} else {
				i++;
			}
		}
		return horizontalExecutionMergeCommits
	}

	def private static fillExecutionLog(MergeCommit lastMergeCommit){
		def out = new File(System.getProperty("user.home") + File.separator + ".jfstmerge" + File.separator + 'execution.log')
		out.append (lastMergeCommit.projectName+','+lastMergeCommit.sha)
		out.append '\n'
	}

	def private static filterByKnownBuilds(List<MergeCommit> mergeCommits){
		println 'Filtering known builds...'
		List<MergeCommit> filtered = new ArrayList<>()
		File f = new File('builds-mergecommits.csv')
		if(!f.exists() ) {
			println "Builds file does not exist!"
		} else {
			//1. group builds by project
			HashMap<String,List<String>> buildsByProject = new HashMap();
			f.eachLine {line ->
				try{
					String[] a = line.split(';')
					String pname = a[0]
					List<String> builds = buildsByProject.get(pname)
					if(builds == null) {
						builds = new ArrayList<>()
						builds.add(line)
						buildsByProject.put(pname, builds)
					}else {
						builds.add(line)
					}
				}catch(Exception e){}
			}
			//2. search builds
			for(MergeCommit m : mergeCommits){
				List<String> builds = buildsByProject.get(m.project.originalName)
				if(builds != null){
					builds.each {entry ->
						if(entry.contains(m.sha)){
							filtered.add(m)
						}
					}
				}
			}
		}
		mergeCommits.clear()
		mergeCommits.addAll(filtered)
	}

	def private static ArrayList<String> restoreExecutionLog(){
		ArrayList<String> alreadyExecutedSHAs = new ArrayList<String>()
		try {
			BufferedReader br = new BufferedReader(new FileReader(System.getProperty("user.home") + File.separator + ".jfstmerge" + File.separator + 'execution.log'))
			String line  = ""
			while ((line = br.readLine()) != null)
				alreadyExecutedSHAs.add(line)
		} catch (FileNotFoundException e) {}
		return alreadyExecutedSHAs
	}

	def private static MergeResult computeScenarioStatistics(MergeCommit m){
		//wrapper
		MergeResult result = new MergeResult()
		result.commit = m;

		//retrieving statistics
		String logpath  = System.getProperty("user.home")+ File.separator + ".jfstmerge" + File.separator;
		File statistics_partial = new File(logpath+ "numbers-current-file.csv");
		File statistics_files 	= new File(logpath+ "numbers-files.csv");
		if(!statistics_files.exists()){(
			new File(logpath)).mkdirs();statistics_files.createNewFile();
			statistics_files.append('project;mergecommit;leftcommit;basecommit;rightcommit;files;consecutiveLinesonly;spacingonly;samePositiononly;sameStmtonly;otheronly;consecutiveLinesAndSamePosition;consecutiveLinesAndsameStmt;otherAndsamePosition;otherAndsameStmt;spacingAndSamePosition;spacingAndSameStmt;ssmergeConf;textualConf;jdimeConfs;smergeTime;textualTime;jdimeTime;sucessfullMerge;isSsEqualsToUn;isStEqualsToUn;changedFiles;commonChangedFiles\n')
		} //ensuring it exists
		/*
		 * numbers-current-file contains numbers for each merged file. To compute numbers for each scenario,
		 * we read the numbers-current-file, and then we delete this file. Thus, every time a merge scenario is merged
		 * the numbers-current-file is empty. To have overall number for all files, regardless merge scenarios, we append
		 * numbers-current-file content in the numbers-files before deleting numbers-current-file.
		 */

		List<String> lines = new ArrayList<String>();
		if(statistics_partial.exists()){lines = statistics_partial.readLines();}
		for(int y = 1/*ignoring header*/; y <lines.size(); y++){
			String[] columns = lines.get(y).split(";");

			statistics_files.append(m.project.originalURL +';'+m.sha +';'+m.parent1 +';'+m.ancestor +';'+m.parent2+';'+lines.get(y)+'\n')

			result.consecutiveLinesonly += Integer.valueOf(columns[1]);
			result.spacingonly 	 += Integer.valueOf(columns[2]);
			result.samePositiononly += Integer.valueOf(columns[3]);
			result.sameStmtonly 	 += Integer.valueOf(columns[4]);
			result.otheronly 		 += Integer.valueOf(columns[5]);
			result.consecutiveLinesAndSamePosition += Integer.valueOf(columns[6]);
			result.consecutiveLinesAndsameStmt 	+= Integer.valueOf(columns[7]);
			result.otherAndsamePosition 	+= Integer.valueOf(columns[8]);
			result.otherAndsameStmt 		+= Integer.valueOf(columns[9]);
			result.spacingAndSamePosition 	+= Integer.valueOf(columns[10]);
			result.spacingAndSameStmt += Integer.valueOf(columns[11]);
			result.ssmergeConf += Integer.valueOf(columns[12]);
			result.textualConf += Integer.valueOf(columns[13]);
			result.jdimeConf   += Integer.valueOf(columns[14]);
			result.ssmergetime += Long.parseLong(columns[15]);
			result.unmergetime += Long.parseLong(columns[16]);
			result.jdimetime   += Long.parseLong(columns[17]);
			result.sucessfullmerge= result.sucessfullmerge&& Boolean.parseBoolean(columns[18]);
			result.isSsEqualsToUn = result.isSsEqualsToUn && Boolean.parseBoolean(columns[19]);
			result.isStEqualsToUn = result.isStEqualsToUn && Boolean.parseBoolean(columns[20]);
			result.changedMethods += Integer.valueOf(columns[21]);
			result.commonChangedMethods += Integer.valueOf(columns[22]);

			result.ssmergeConfNoCL = result.ssmergeConf - result.consecutiveLinesonly;
			result.ssmergeConfNoCL = (result.ssmergeConfNoCL < 0) ? 0 : result.ssmergeConfNoCL;
			result.ssmergeConfNoWS = result.ssmergeConf - result.spacingonly;
			result.ssmergeConfNoWS = (result.ssmergeConfNoWS < 0) ? 0 : result.ssmergeConfNoWS;
			result.ssmergeConfNoCLWS = result.ssmergeConf - (result.spacingonly + result.consecutiveLinesonly);
			result.ssmergeConfNoCLWS = (result.ssmergeConfNoCLWS < 0) ? 0 : result.ssmergeConfNoCLWS;
		}

		Set<String> changedFiles = new HashSet<String>(m.changedRightFiles);
		changedFiles.addAll(m.changedLeftFiles); //Set to avoid duplicates
		List<String> commonChangedFiles = new ArrayList<String>(m.changedRightFiles)
		commonChangedFiles.retainAll(m.changedLeftFiles) //twice to filter already common elements in the set

		result.changedFiles = changedFiles.size()
		//result.changedFiles = (m.changedLeftFiles.size() + m.changedRightFiles.size()) - (2*commonChangedFiles.size())
		result.commonChangedFiles = commonChangedFiles.size()

		(new AntBuilder()).delete(file:statistics_partial.getAbsolutePath(),failonerror:false)
		return result
	}

	def private static computeProjectStatistics(){
		Map<String, ProjecResult> projectsStastitics = new HashMap<>()
		File scenarios = new File("conflictingJDIME/numbers-scenarios.csv")
		List<String> lines = new ArrayList<String>()
		if(scenarios.exists()){
			lines = scenarios.readLines();
			for(int y = 1/*ignoring header*/; y <lines.size(); y++){
				String[] columns = lines.get(y).split(";");
				String project = columns[0];
				ProjecResult result = projectsStastitics.get(project)
				if(result == null){
					result = new ProjecResult()
					result.projectName = project
					projectsStastitics.put(project, result)
				}
				int CL = Integer.valueOf(columns[5]);
				int WS = Integer.valueOf(columns[6]);
				int ssmergeConf = Integer.valueOf(columns[16]);
				int textualConf = Integer.valueOf(columns[17]);
				int jdimeConf = Integer.valueOf(columns[18]);

				result.numberOfScenarios++;
				result.conflictingScenarios = (ssmergeConf > 0 || jdimeConf > 0) ? result.conflictingScenarios + 1 : result.conflictingScenarios;
				result.conflictingScenariosNoWS = ((ssmergeConf - (WS)) > 0 || jdimeConf > 0) ? result.conflictingScenariosNoWS + 1 : result.conflictingScenariosNoWS;
				result.conflictingScenariosNoCLWS = ((ssmergeConf - (CL + WS)) > 0 || jdimeConf > 0) ? result.conflictingScenariosNoCLWS + 1 : result.conflictingScenariosNoCLWS;

				result.scenariosWithCL = (CL > 0) ? (result.scenariosWithCL + 1) : result.scenariosWithCL;
				result.scenariosWithWS = (WS > 0) ? (result.scenariosWithWS + 1) : result.scenariosWithWS;
				result.scenariosWithSsmergeConf = (ssmergeConf > 0) ? (result.scenariosWithSsmergeConf + 1) : result.scenariosWithSsmergeConf;
				result.scenariosWithTextualConf = (textualConf > 0) ? (result.scenariosWithTextualConf + 1) : result.scenariosWithTextualConf;
				result.scenariosWithJdimeConf = (jdimeConf > 0) ? (result.scenariosWithJdimeConf + 1) : result.scenariosWithJdimeConf;

				result.scenariosOnlyWithSsmergeConf = (ssmergeConf > 0 && jdimeConf == 0) ? (result.scenariosOnlyWithSsmergeConf + 1) : result.scenariosOnlyWithSsmergeConf;
				result.scenariosOnlyWithTextualConf = (textualConf > 0 && jdimeConf == 0 && ssmergeConf == 0) ? (result.scenariosOnlyWithTextualConf + 1) : result.scenariosOnlyWithTextualConf;
				result.scenariosOnlyWithJdimeConf = (ssmergeConf == 0 && jdimeConf > 0) ? (result.scenariosOnlyWithJdimeConf + 1) : result.scenariosOnlyWithJdimeConf;

				result.scenariosOnlyWithSsmergeConfNoCLWS = ((ssmergeConf - (CL + WS))> 0 && jdimeConf == 0) ? (result.scenariosOnlyWithSsmergeConfNoCLWS + 1) : result.scenariosOnlyWithSsmergeConfNoCLWS;
				result.scenariosOnlyWithSsmergeConfNoWS = ((ssmergeConf - (WS))> 0 && jdimeConf == 0) ? (result.scenariosOnlyWithSsmergeConfNoWS + 1) : result.scenariosOnlyWithSsmergeConfNoWS;
				result.scenariosOnlyWithJdimeConfNoCLWS = ((ssmergeConf - (CL + WS)) <= 0 && jdimeConf > 0) ? (result.scenariosOnlyWithJdimeConfNoCLWS + 1) : result.scenariosOnlyWithJdimeConfNoCLWS;
				result.scenariosOnlyWithJdimeConfNoWS = ((ssmergeConf - (WS)) <= 0 && jdimeConf > 0) ? (result.scenariosOnlyWithJdimeConfNoWS + 1) : result.scenariosOnlyWithJdimeConfNoWS;

				result.consecutiveLinesonly += CL;
				result.spacingonly += WS;
				result.ssmergeConf += ssmergeConf;
				result.textualConf += textualConf;
				result.jdimeConf   += jdimeConf;
				result.ssmergetime += Long.parseLong(columns[19]);
				result.unmergetime += Long.parseLong(columns[20]);
				result.jdimetime   += Long.parseLong(columns[21]);

				String travisStatus = columns[25];
				if(travisStatus.equals("PASSED")){
					result.travisPASSEDssmerge++;
				} else if(travisStatus.equals("FAILED")){
					result.travisFAILEDssmerge++;
				} else if(travisStatus.equals("ERRORED")){
					result.travisERROREDssmerge++
				} else {
					if(!travisStatus.equals("NONE")) result.travisBuildDiscarded++
				}

				result.travisBuildTime += Long.parseLong(columns[28]);

				int commonChangedFiles = Integer.valueOf(columns[30]);
				int commonChagendMethods = Integer.valueOf(columns[32]);

				result.changedFiles += Integer.valueOf(columns[29]);
				result.commonChangedFiles += commonChangedFiles;
				result.changedMethods += Integer.valueOf(columns[31]);
				result.commonChangedMethods += commonChagendMethods;

				result.ssmergeConfNoCL = result.ssmergeConf - result.consecutiveLinesonly;
				result.ssmergeConfNoCL = (result.ssmergeConfNoCL < 0) ? 0 : result.ssmergeConfNoCL;
				result.ssmergeConfNoWS = result.ssmergeConf - result.spacingonly;
				result.ssmergeConfNoWS = (result.ssmergeConfNoWS < 0) ? 0 : result.ssmergeConfNoWS;
				result.ssmergeConfNoCLWS = result.ssmergeConf - (result.spacingonly + result.consecutiveLinesonly);
				result.ssmergeConfNoCLWS = (result.ssmergeConfNoCLWS < 0) ? 0 : result.ssmergeConfNoCLWS;

				result.scenariosWithSsmergeConfNoCL = ((ssmergeConf - CL) > 0) ? (result.scenariosWithSsmergeConfNoCL + 1) : result.scenariosWithSsmergeConfNoCL;
				result.scenariosWithSsmergeConfNoWS = ((ssmergeConf - WS) > 0) ? (result.scenariosWithSsmergeConfNoWS + 1) : result.scenariosWithSsmergeConfNoWS;
				result.scenariosWithSsmergeConfNoCLWS = ((ssmergeConf - (CL + WS)) > 0) ? (result.scenariosWithSsmergeConfNoCLWS + 1) : result.scenariosWithSsmergeConfNoCLWS;

				result.scenariosWithCommonChangedFiles = (commonChangedFiles > 0) ? result.scenariosWithCommonChangedFiles + 1 : result.scenariosWithCommonChangedFiles;
				result.scenariosWithCommonChangedMethods = (commonChagendMethods > 0) ? result.scenariosWithCommonChangedMethods + 1 : result.scenariosWithCommonChangedMethods;

			}

			//getting builds info for jdime output
			if(!skip_2nd_run){
				scenarios = new File("conflictingS3M/numbers-scenarios.csv")
				lines = new ArrayList<String>()
				if(scenarios.exists()){
					lines = scenarios.readLines();
					for(int y = 1/*ignoring header*/; y <lines.size(); y++){
						String[] columns = lines.get(y).split(";");
						String project = columns[0];
						ProjecResult result = projectsStastitics.get(project)

						int CL = Integer.valueOf(columns[5]);
						int WS = Integer.valueOf(columns[6]);
						int ssmergeConf = Integer.valueOf(columns[16]);

						if(result == null){
							throw new RuntimeException('ERROR: missing projects results for conflictingJDIME/numbers-scenarios.csv')
						} else {
							String travisStatus = columns[25];
							if(travisStatus.equals("PASSED")){
								result.travisPASSEDjdime++;
								if((ssmergeConf - (WS)) > 0){
									result.travisPASSEDjdimeNoWS++;
								}
								if((ssmergeConf - (CL + WS)) > 0){
									result.travisPASSEDjdimeNoCLWS++;
								}
							} else if(travisStatus.equals("FAILED")){
								result.travisFAILEDjdime++;
								if((ssmergeConf - (WS)) > 0){
									result.travisFAILEDjdimeNoWS++;
								}
								if((ssmergeConf - (CL + WS)) > 0){
									result.travisFAILEDjdimeNoCLWS++;
								}
							} else if(travisStatus.equals("ERRORED")){
								result.travisERROREDjdime++
								if((ssmergeConf - (WS)) > 0){
									result.travisERROREDjdimeNoWS++;
								}
								if((ssmergeConf - (CL + WS)) > 0){
									result.travisERROREDjdimeNoCLWS++;
								}
							} else {
								if(!travisStatus.equals("NONE")) result.travisBuildDiscarded++
							}
							result.travisBuildTime += Long.parseLong(columns[28]);
						}
					}
				}else {
					throw new RuntimeException( 'ERROR: unable to find conflictingS3M/numbers-scenarios.csv to compute projects results')
				}
			}
		} else {
			throw new RuntimeException( 'ERROR: unable to find conflictingJDIME/numbers-scenarios.csv to compute projects results')
		}

		//priting project statistics
		for(ProjecResult p : projectsStastitics.values()){
			logProjectStatistics(p)
		}
	}

	private static computeManualAnalysisFiles() {
		println 'Generating manual analysis files...'
		List<String> sheet = new ArrayList<>();
		List<String> lines = new ArrayList<>();

		List<MergedLog> mls = Extractor.revertMergedLog(new File('conflictingJDIME/confsjdime.txt'));
		File scenarios = new File("conflictingJDIME/numbers-scenarios.csv")
		if(scenarios.exists()){
			lines = scenarios.readLines();
			lines = lines.findAll {String l ->l.contains('PASSED')}
			sheet.addAll(processComputedResults(lines, mls))
		}

		mls = Extractor.revertMergedLog(new File('conflictingS3M/confsjfstmerge.txt'));
		scenarios = new File("conflictingS3M/numbers-scenarios.csv")
		if(scenarios.exists()){
			lines = scenarios.readLines();
			lines = lines.findAll {String l ->l.contains('PASSED')}
			sheet.addAll(processComputedResults(lines, mls))
		}

		File manualAnalysisFile = new File("manualAnalysis.csv");
		if(!manualAnalysisFile.exists()){
			def header = 'Merge Scenario Identifier;Project;Conflicting Merged File;Summary Conflict-related Left Changes;Summary Conflict-related Right Changes;Summary Semistructured Merge Result;Summary Structured Merge Result;Conflicting Tool;Merge Conflict Classification;Justificative';
			manualAnalysisFile.append(header+'\n')
			manualAnalysisFile.createNewFile()
		} //ensuring it exists
		sheet.each {String s ->
			manualAnalysisFile.append(s+'\n')
		}
	}

	def private static logScenarioStatistics(MergeResult result) {
		String logpath = System.getProperty("user.home")+ File.separator + ".jfstmerge" + File.separator;
		File statistics_scenarios = new File(logpath+ "numbers-scenarios.csv");
		if(!statistics_scenarios.exists()){
			def header = "project;mergecommit;leftcommit;basecommit;rightcommit;consecutiveLinesConf;spacingConf;samePositiononly;sameStmtonly;otheronly;consecutiveLinesAndSamePosition;consecutiveLinesAndsameStmt;otherAndsamePosition;otherAndsameStmt;spacingAndSamePosition;spacingAndSameStmt;ssmergeConf;textualConf;jdimeConfs;smergeTime;textualTime;jdimeTime;sucessfullMerge;isSsEqualsToUn;isStEqualsToUn;travisStatus;travisBuildURI;travisJobURI;travisBuildTime;changedFiles;commonChangedFiles;changedMethods;commonChangedMethods;ssmergeConfNoCL;ssmergeConfNoWS;ssmergeConfNoCLWS"
			statistics_scenarios.append(header+'\n')
			statistics_scenarios.createNewFile()
		} //ensuring it exists

		def loggermsg = result.commit.projectName + ';' + result.commit.sha + ';' + result.commit.parent1 + ';' + result.commit.ancestor + ';' + result.commit.parent2 + ';' + result.consecutiveLinesonly + ';'+ result.spacingonly + ';'+ result.samePositiononly + ';'+ result.sameStmtonly + ';'+ result.otheronly + ';'+ result.consecutiveLinesAndSamePosition + ';'	+ result.consecutiveLinesAndsameStmt + ';'+ result.otherAndsamePosition + ';'+ result.otherAndsameStmt + ';'+ result.spacingAndSamePosition + ';'+ result.spacingAndSameStmt + ';'+ result.ssmergeConf + ';'+ result.textualConf + ';'+ result.jdimeConf + ';'+ result.ssmergetime + ';'+ result.unmergetime + ';'+ result.jdimetime + ';'+ result.sucessfullmerge+ ';'+ result.isSsEqualsToUn + ';'+ result.isStEqualsToUn + ';'+ result.travisStatus + ';'+ result.travisBuildURI + ';'+ result.travisJobURI + ';'+ result.travisBuildTime  + ';'+ result.changedFiles  + ';'+ result.commonChangedFiles + ';'+ result.changedMethods + ';'+ result.commonChangedMethods + ';'+ result.ssmergeConfNoCL+ ';' +result.ssmergeConfNoWS+ ';' +result.ssmergeConfNoCLWS;
		statistics_scenarios.append(loggermsg+'\n')
	}

	def private static logProjectStatistics(ProjecResult result) {
		File statistics_scenarios = new File("numbers-projects.csv");
		if(!statistics_scenarios.exists()){
			def header = "projectName;numberOfScenarios;conflictingScenarios;conflictingScenariosNoWS;conflictingScenariosNoCLWS;scenariosWithCommonChangedFiles;scenariosWithCommonChangedMethods;scenariosWithSsmergeConf;scenariosWithTextualConf;scenariosWithJdimeConf;scenariosWithCL;scenariosWithWS;consecutiveLinesConf;spacingConf;ssmergeConf;jdimeConf;textualConf;ssmergetime;textualtime;jdimetime;travisBuildTime;travisBuildDiscarded;travisPASSEDssmerge;travisFAILEDssmerge;travisERROREDssmerge;travisPASSEDjdime;travisFAILEDjdime;travisERROREDjdime;changedFiles;commonChangedFiles;changedMethods;commonChangedMethods;ssmergeConfNoCL;ssmergeConfNoWS;ssmergeConfNoCLWS;scenariosWithSsmergeConfNoCL;scenariosWithSsmergeConfNoWS;scenariosWithSsmergeConfNoCLWS;scenariosOnlyWithSsmergeConf;scenariosOnlyWithTextualConf;scenariosOnlyWithJdimeConf;scenariosOnlyWithSsmergeConfNoCLWS;scenariosOnlyWithJdimeConfNoCLWS;scenariosOnlyWithSsmergeConfNoWS;scenariosOnlyWithJdimeConfNoWS;travisPASSEDjdimeNoWS;travisFAILEDjdimeNoWS;travisERROREDjdimeNoWS;travisPASSEDjdimeNoCLWS;travisFAILEDjdimeNoCLWS;travisERROREDjdimeNoCLWS"
			statistics_scenarios.append(header+'\n')
			statistics_scenarios.createNewFile()
		} //ensuring it exists

		def loggermsg = result.projectName + ';' +result.numberOfScenarios + ';' +result.conflictingScenarios + ';' +result.conflictingScenariosNoWS + ';' +result.conflictingScenariosNoCLWS + ';' +result.scenariosWithCommonChangedFiles + ';' +result.scenariosWithCommonChangedMethods + ";" + result.scenariosWithSsmergeConf + ";" + result.scenariosWithTextualConf + ";" + result.scenariosWithJdimeConf + ';' +result.scenariosWithCL + ';' +result.scenariosWithWS + ';' +result.consecutiveLinesonly+ ';' +result.spacingonly+ ';' +result.ssmergeConf+ ';' +result.jdimeConf+ ';' +result.textualConf+ ';' +result.ssmergetime+ ';' +result.unmergetime+ ';' +result.jdimetime+ ';' +result.travisBuildTime+ ';' +result.travisBuildDiscarded+ ';' +result.travisPASSEDssmerge+ ';' +result.travisFAILEDssmerge+ ';' +result.travisERROREDssmerge+ ';' +result.travisPASSEDjdime+ ';' +result.travisFAILEDjdime+ ';' +result.travisERROREDjdime+ ';' +result.changedFiles+ ';' +result.commonChangedFiles+ ';' +result.changedMethods+ ';' +result.commonChangedMethods + ';'+ result.ssmergeConfNoCL+ ';' +result.ssmergeConfNoWS+ ';' +result.ssmergeConfNoCLWS + ';'+ result.scenariosWithSsmergeConfNoCL+ ';' +result.scenariosWithSsmergeConfNoWS+ ';' +result.scenariosWithSsmergeConfNoCLWS + ";" + result.scenariosOnlyWithSsmergeConf + ";" + result.scenariosOnlyWithTextualConf + ";" + result.scenariosOnlyWithJdimeConf + ";" + result.scenariosOnlyWithSsmergeConfNoCLWS + ";" + result.scenariosOnlyWithJdimeConfNoCLWS + ";" + result.scenariosOnlyWithSsmergeConfNoWS + ";" + result.scenariosOnlyWithJdimeConfNoWS+ ';' +result.travisPASSEDjdimeNoWS+ ';' +result.travisFAILEDjdimeNoWS+ ';' +result.travisERROREDjdimeNoWS + ';' +result.travisPASSEDjdimeNoCLWS+ ';' +result.travisFAILEDjdimeNoCLWS+ ';' +result.travisERROREDjdimeNoCLWS;
		statistics_scenarios.append(loggermsg+'\n')
	}

	def private static processComputedResults(List lines, List mls) {
		List<String> sheetLines = new ArrayList<>();
		for(int y = 0; y <lines.size(); y++){
			String[] columns = lines.get(y).split(";");
			String project = columns[0];
			String mergeCommit = columns[1]

			int CL = Integer.valueOf(columns[5]);
			int WS = Integer.valueOf(columns[6]);

			int ssmergeConf = Integer.valueOf(columns[16]);
			int textualConf = Integer.valueOf(columns[17]);
			int jdimeConf = Integer.valueOf(columns[18]);

			int ssmergeConfNoCL = ssmergeConf - CL;
			int ssmergeConfNoWS = ssmergeConf - WS;
			int ssmergeConfNoCLWS = ssmergeConf - (CL + WS);

			String travisStatus = columns[25];
			if(travisStatus.equals("PASSED")){
				MergedLog mL = mls.find{
					it.mergeCommit.trim() == mergeCommit.trim()
				}
				if(mL != null){
					// create files with merged content
					writeManualAnalysisFile(mL.mergeCommit, mL.mergedFile, mL.BaseContent, '_base');
					writeManualAnalysisFile(mL.mergeCommit, mL.mergedFile, mL.LeftContent, '_left');
					writeManualAnalysisFile(mL.mergeCommit, mL.mergedFile, mL.RightContent,'_right');
					writeManualAnalysisFile(mL.mergeCommit, mL.mergedFile, mL.SemistructuredMergeOutput, '_semistructured');
					writeManualAnalysisFile(mL.mergeCommit, mL.mergedFile, mL.StructuredMergeOutput, '_structured');

					//fill sheet's entry
					StringBuilder builder = new StringBuilder();
					builder.append(mL.mergeCommit);
					builder.append(';');
					builder.append('https://github.com/spgroup/s3m/blob/master/svj/manualAnalysis/' + mL.projectName);
					builder.append(';');
					builder.append('https://github.com/spgroup/s3m/blob/master/svj/manualAnalysis/' + mL.mergeCommit + '/' + mL.mergedFile);
					builder.append(';');
					builder.append(';');
					builder.append(';');
					builder.append(';');
					builder.append(';');
					builder.append(jdimeConf > 0 ? 'Structured Merge' : 'Semistructured Merge');
					builder.append(';');
					sheetLines.add(builder.toString());
				}
			}
		}
		return sheetLines;
	}



	def private static File writeManualAnalysisFile(String mergecommit, String filename, String content, String suffix) {
		File mLfile = new File('manualAnalysis/'+ mergecommit + '/' + filename);
		mLfile.mkdirs();
		mLfile = new File(mLfile.getAbsolutePath() + '/' + filename + suffix + '.java');
		mLfile.append(content);
	}

	static void m(){
		Map<String, String> oldmap = new LinkedHashMap<>();
		(new File('conflictingS3M/numbers-scenarios.csv')).eachLine { String line ->
			String[] columns = line.split(';');
			String key = columns[0] + columns[1];
			oldmap.put(key,line);
		}

		Map<String, String> newmap = new LinkedHashMap<>();
		int j = 1;
		(new File('numbers-scenarios.csv')).eachLine { String line ->
			String[] columns = line.split(';');
			String key = columns[0] + columns[1];
			newmap.put(key,line);
		}

		String result = "";
		List<String> oldkeys = new ArrayList<>(oldmap.keySet());
		for(int i = 0; i < oldkeys.size(); i++){
			String key = oldkeys.get(i);
			String oldline = oldmap.get(key);
			String newline = newmap.get(key);
			if(newline != null){
				result = result + newline + '\n';
			} else {
				result = result + oldline + '\n';
			}
		}

		def out = new File('numbers-scenarios-new.csv')
		out.write result;
	}

	static void n(){
		Map<String, String> oldmap = new LinkedHashMap<>();
		(new File('builds-mergecommits.csv')).eachLine { String line ->
			String[] columns = line.split(';');
			String key = columns[3];
			oldmap.put(key,columns[0]);
		}

		Map<String, String> newmap = new LinkedHashMap<>();
		int j = 1;
		(new File('in.in')).eachLine { String line ->
			String value = oldmap.get(line)
			println value
		}
	}
}