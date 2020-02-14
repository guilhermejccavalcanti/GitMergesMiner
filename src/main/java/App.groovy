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
	static boolean mine_local = false;
	static boolean mine_web = false;


	public static void main (String[] args){
		/*
		 //execution configuration parameters
		 def cli = new CliBuilder()
		 cli.with {
		 l longOpt: 'local', 'mine local repositories'
		 w longOpt: 'web', 'mine remote repositories'
		 r longOpt: 'remote', 'do not restore repositories'
		 }
		 def options = cli.parse(args)
		 if (options.l) {
		 println 'Mining local repositories...'
		 mine_local = true
		 mine_web = false
		 }
		 if (options.w) {
		 println 'Mining remote repositories...'
		 mine_web = true
		 mine_local = false
		 }
		 if (options.r) {
		 restore_repositories = false
		 }
		 if(!mine_local && !mine_web) {
		 println  'ERROR: choose a mining option -l (local) or -w(remote)'
		 } else {
		 //managing execution log
		 File f = new File(System.getProperty("user.home") + File.separator + ".jfstmerge" + File.separator + 'execution.log')
		 if(!f.exists()){
		 f.getParentFile().mkdirs()
		 f.createNewFile()
		 }
		 //configuring git merge driver
		 File gitconfig = new File(System.getProperty("user.home") + File.separator + ".gitconfig")
		 if(!gitconfig.exists()) {
		 throw new RuntimeException( 'ERROR: .gitconfig not found on ' + gitconfig.getParent() + '. S3M tool not installed?')
		 }
		 String gitconfigContents =  gitconfig.text
		 if(!gitconfig.text.contains("-c false")){
		 gitconfig.text = gitconfigContents.replaceAll("-g", "-g -c false")
		 }
		 //running git merges
		 run_gitmerges()
		 //storing results
		 def resultsFolder = "results";
		 (new File(resultsFolder)).deleteDir() //deleting old files
		 boolean success = f.getParentFile().renameTo(new File(resultsFolder))
		 if(!success){
		 throw new RuntimeException( 'ERROR: unable to store run results')
		 }
		 computeProjectStatistics()
		 if(gitconfig.text.contains("-c false")){
		 gitconfig.text = gitconfigContents.replaceAll("-c false", "")
		 }
		 println 'DONE!'
		 }*/
		//computeManualAnalysisFiles("fpss")
		//computeManualAnalysisFiles("fpun")
		computeManualAnalysisFiles("fnss")

		println 'funfou!'
	}

	def public static run_gitmerges(){
		Read r = new Read("projects.csv",false)
		def projects = r.getProjects()
		if(restore_repositories){
			restoreGitRepositories(projects)
		}

		//fill merge scenarios info (base,left,right)
		projects.each {
			Extractor.init(it,false)
			Extractor.fillAncestors()
			println('Project ' + it.name + " read")
		}

		//reproduce the 'git merge' command for each merge scenario
		LinkedList<MergeCommit> merge_commits = fillMergeCommitsListForHorizontalExecution(projects)
		for(int i=0; i<merge_commits.size();i++){
			MergeCommit m = merge_commits.get(i);
			println ('Analysing ' + ((i+1)+'/'+merge_commits.size()) + ': ' +  m.sha)
			fillExecutionLog(m)
			Extractor.init(m.project, true)
			Extractor.git_diff(m)
			Extractor.git_merge(m)

			//compute statistics related to each scenario
			MergeResult result = computeScenarioStatistics(m)

			//log statistics related to each scenario
			logScenarioStatistics(result)
		}
		println 'Analysis Finished!'
	}

	def private static restoreGitRepositories(ArrayList<Project> projects){
		projects.each {
			Extractor.init(it,true)
			Extractor.restoreWorkingFolder()
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
		File statistics_partial = new File(logpath+ "jfstmerge.statistics");
		File statistics_files = new File(logpath+ "jfstmerge.statistics.files");
		if(!statistics_files.exists()){(
			new File(logpath)).mkdirs();statistics_files.createNewFile();
			statistics_files.append('project;mergecommit;leftcommit;basecommit;rightcommit;files;ssmergeconfs;ssmergeloc;ssmergerenamingconfs;ssmergedeletionconfs;ssmergeinnerdeletionconfs;ssmergetaeconfs;ssmergenereoconfs;ssmergeinitlblocksconfs;ssmergeacidentalconfs;unmergeconfs;unmergeloc;unmergetime;ssmergetime;unmergeduplicateddeclarationerrors;unmergeorderingconfs;equalconfs\n')
		} //ensuring it exists
		/*
		 * jfstmerge.statistics contains numbers for each merged file. To compute numbers for each scenario,
		 * we read jfstmerge.statistics, and then we delete this file. Thus, every time a merge scenario is merged
		 * the jfstmerge.statistics is empty. To have overall number for all files, regardless merge scenarios, we append
		 * jfstmerge.statistics content in the jfstmerge.statistics.files before deleting jfstmerge.statistics.
		 */

		List<String> lines = new ArrayList<String>();
		if(statistics_partial.exists()){lines = statistics_partial.readLines();}
		for(int y = 1/*ignoring header*/; y <lines.size(); y++){
			String[] columns = lines.get(y).split(";");
			statistics_files.append(m.project.url +';'+m.sha +';'+m.parent1 +';'+m.ancestor +';'+m.parent2+';'+ (String.join(";", Arrays.copyOfRange(columns, 3, columns.length))) +'\n')

			//			result.ssmergeconfs += Integer.valueOf(columns[4]);
			//			result.ssmergeloc += Integer.valueOf(columns[5]);
			//			result.ssmergerenamingconfs+= Integer.valueOf(columns[6]);
			//			result.ssmergedeletionconfs+= Integer.valueOf(columns[7]);
			//			result.ssmergeinnerdeletionconfs+= Integer.valueOf(columns[8]);
			//			result.ssmergetaeconfs+= Integer.valueOf(columns[9]);
			//			result.ssmergenereoconfs+= Integer.valueOf(columns[10]);
			//			result.ssmergeinitlblocksconfs+= Integer.valueOf(columns[11]);
			//			result.ssmergeacidentalconfs+= Integer.valueOf(columns[12]);
			//			result.unmergeconfs+= Integer.valueOf(columns[13]);
			//			result.unmergeloc+= Integer.valueOf(columns[14]);
			//			result.unmergetime+= Integer.valueOf(columns[15]);
			//			result.ssmergetime+= Integer.valueOf(columns[16]);
			//			result.unmergeduplicateddeclarationerrors+= Integer.valueOf(columns[17]);
			//			result.unmergeorderingconfs+= Integer.valueOf(columns[18]);
			//			result.equalconfs+= Integer.valueOf(columns[19]);

			result.ssmergeconfs += Integer.parseInt(columns[2]);
			result.ssmergeloc 	 += Integer.parseInt(columns[3]);
			result.ssmergerenamingconfs += Integer.parseInt(columns[4]);
			result.ssmergedeletionconfs += Integer.parseInt(columns[5]);
			result.ssmergetaeconfs   += Integer.parseInt(columns[7]);
			result.ssmergenereoconfs += Integer.parseInt(columns[8]);
			result.ssmergeinitlblocksconfs += Integer.parseInt(columns[9]);
			result.ssmergeacidentalconfs 	+= Integer.parseInt(columns[10]);
			result.unmergeconfs += Integer.parseInt(columns[11]);
			result.unmergeloc 	 += Integer.parseInt(columns[12]);
			result.unmergetime  += Long.parseLong(columns[13]);
			result.ssmergetime  += Long.parseLong((columns[14]));
			result.unmergeduplicateddeclarationerrors += Integer.parseInt(columns[15]);
			result.unmergeorderingconfs += Integer.parseInt(columns[16]);
			result.equalconfs 	 += Integer.parseInt(columns[17]);

		}

		(new AntBuilder()).delete(file:statistics_partial.getAbsolutePath(),failonerror:false)
		return result
	}

	def private static computeProjectStatistics(){
		List<String> diverginScenarios = new ArrayList<String>();

		Map<String, ProjecResult> projectsStastitics = new HashMap<>()
		File scenarios = new File("results/numbers-scenarios.csv")
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

				int ssmergeConf = Integer.valueOf(columns[5]);
				int textualConf = Integer.valueOf(columns[14]);
				int ssmergerenamingconfs = Integer.valueOf(columns[7]);
				int ssmergedeletionconfs = Integer.valueOf(columns[8]);
				int ssmergeinnerdeletionconfs= Integer.valueOf(columns[9]);
				int ssmergetaeconfs= Integer.valueOf(columns[10]);
				int ssmergenereoconfs= Integer.valueOf(columns[11]);
				int ssmergeinitlblocksconfs= Integer.valueOf(columns[12]);
				int ssmergeacidentalconfs= Integer.valueOf(columns[13]);
				int unmergeduplicateddeclarationerrors= Integer.valueOf(columns[18]);
				int unmergeorderingconfs= Integer.valueOf(columns[19]);

				result.ssmergeconfs += ssmergeConf;
				result.ssmergeloc += textualConf;
				result.ssmergerenamingconfs+= ssmergerenamingconfs
				result.ssmergedeletionconfs+= ssmergedeletionconfs
				result.ssmergeinnerdeletionconfs+= ssmergeinnerdeletionconfs
				result.ssmergetaeconfs+= ssmergetaeconfs
				result.ssmergenereoconfs+= ssmergenereoconfs
				result.ssmergeinitlblocksconfs+= ssmergeinitlblocksconfs
				result.ssmergeacidentalconfs+= ssmergeacidentalconfs

				result.unmergeconfs+= Integer.valueOf(columns[14]);
				result.unmergeloc+= Integer.valueOf(columns[15]);
				result.unmergetime+= Long.parseLong(columns[16]);
				result.ssmergetime+= Long.parseLong(columns[17]);

				result.unmergeduplicateddeclarationerrors+= unmergeduplicateddeclarationerrors
				result.unmergeorderingconfs+= unmergeorderingconfs

				result.equalconfs+= Integer.valueOf(columns[20]);

				result.numberOfScenarios++;
				result.conflictingScenarios = (ssmergeConf > 0 || textualConf > 0) ? result.conflictingScenarios + 1 : result.conflictingScenarios;

				result.scenariosWithSsmergeConf = (ssmergeConf > 0) ? (result.scenariosWithSsmergeConf + 1) : result.scenariosWithSsmergeConf;
				result.scenariosWithTextualConf = (textualConf > 0) ? (result.scenariosWithTextualConf + 1) : result.scenariosWithTextualConf;

				result.scenariosOnlyWithSsmergeConf = (ssmergeConf > 0 && textualConf == 0) ? (result.scenariosOnlyWithSsmergeConf + 1) : result.scenariosOnlyWithSsmergeConf;
				result.scenariosOnlyWithTextualConf = (textualConf > 0 && ssmergeConf == 0) ? (result.scenariosOnlyWithTextualConf + 1) : result.scenariosOnlyWithTextualConf;

				result.scenariosWithRenamingConfs = (ssmergerenamingconfs > 0) ? (result.scenariosWithRenamingConfs + 1) : result.scenariosWithRenamingConfs;
				result.scenariosWithDeletionConfs = (ssmergedeletionconfs > 0) ? (result.scenariosWithDeletionConfs + 1) : result.scenariosWithDeletionConfs;
				result.scenariosWithTaeConfs = (ssmergetaeconfs > 0) ? (result.scenariosWithTaeConfs + 1) : result.scenariosWithTaeConfs;
				result.scenariosWithNereoConfs = (ssmergenereoconfs > 0) ? (result.scenariosWithNereoConfs + 1) : result.scenariosWithNereoConfs;
				result.scenariosWithInitBlocksConfs = (ssmergeinitlblocksconfs > 0) ? (result.scenariosWithInitBlocksConfs + 1) : result.scenariosWithInitBlocksConfs;
				result.scenariosWithAcidentalConfs = (ssmergeacidentalconfs > 0) ? (result.scenariosWithAcidentalConfs + 1) : result.scenariosWithAcidentalConfs;

				result.scenariosWithOrderingConfs = (unmergeorderingconfs > 0) ? (result.scenariosWithOrderingConfs + 1) : result.scenariosWithOrderingConfs;
				result.scenariosWithDuplicatedDeclarations = (unmergeduplicateddeclarationerrors > 0) ? (result.scenariosWithDuplicatedDeclarations + 1) : result.scenariosWithDuplicatedDeclarations;

				if((ssmergeConf > 0 && textualConf == 0) || (textualConf > 0 && ssmergeConf == 0)){
					diverginScenarios.add((String.join(";", Arrays.copyOfRange(columns, 0, 5))) + ";" + ssmergeConf + ";" + textualConf )
				}
			}
		} else {
			throw new RuntimeException( 'ERROR: unable to find results/numbers-scenarios.csv to compute projects results')
		}

		//priting project statistics
		for(ProjecResult p : projectsStastitics.values()){
			logProjectStatistics(p)
		}

		//printing diverging scenarios
		logDivergingScenarios(diverginScenarios)
	}

	def private static computeManualAnalysisFiles(String metric) {
		println 'Generating manual analysis files for ' + metric + ' builds...'
		List<String> sheet = new ArrayList<>();
		List<String> lines = new ArrayList<>();

		File log;
		if(metric.equals("fpss")){
			log = new File('results/conflicts.semistructured')
		} else {
			log = new File('results/conflicts.unstructured')
		}

		List<MergedLog> mls = Extractor.revertMergedLog(log);
		File scenarios = new File("results/numbers-scenarios.csv")
		if(scenarios.exists()){
			lines = scenarios.readLines();
			sheet.addAll(processComputedResults(lines, mls, metric))
		}

		File manualAnalysisFile = new File("manualAnalysis_"+metric+".csv");
		if(!manualAnalysisFile.exists()){
			def header = 'Merge Scenario Identifier;Project;Conflicting Merged File;Tool Analysed;Comments About Resolution Effort';
			if(metric.equals("fnss")){
				//header = 'Merge Scenario Identifier;Project;Conflicting Merged File;Summary Conflict-related Left Changes;Summary Conflict-related Right Changes;Tool Analysed;Merge Conflict Classification;Justificative';
				header = 'Merge Scenario Identifier;Project;Conflicting Merged File;Summary Conflict-related Left Changes;Summary Conflict-related Right Changes;Unstructured Merge Conflict Classification;Semistructured Merge Conflict Classification;Justificative';
				// Alguns conflitos do não-estruturado são classificados como falsos negativos acidentais. O objetivo é analisar esse conflitos e classificá-los como verdadeiro positivo, ou falso negativo.
				//Para isso, recomendo ignorar os conflitos comuns/equivalentes entre as duas ferramentas nos arquivos, e analisar só o restante no arquivo integrado pelo não-estruturado.
			}
			manualAnalysisFile.append(header+'\n')
			manualAnalysisFile.createNewFile()
		} //ensuring it exists
		sheet.each {String s ->
			manualAnalysisFile.append(s+'\n')
		}
	}

	def private static processComputedResults(List lines, List mls, String metric) {
		List<String> sheetLines = new ArrayList<>();
		for(int y = 1; y <lines.size(); y++){
			String[] columns = lines.get(y).split(";");
			String project = columns[0];
			String mergeCommit = columns[1]

			int ssmergeConf = Integer.valueOf(columns[5]);
			int ssmergerenamingconfs = Integer.valueOf(columns[7]);
			int ssmergeacidentalconfs = Integer.valueOf(columns[13]);

			int textualConf = Integer.valueOf(columns[14]);
			int textualorderingconfs = Integer.valueOf(columns[19]);

			int equalconfs = Integer.valueOf(columns[20]);

			boolean condition = false;
			if(metric.equals("fpss")){
				condition = ssmergerenamingconfs > 0;
			} else if(metric.equals("fpun")){
				condition = (textualorderingconfs > 0) && (textualorderingconfs > equalconfs);
			} else if(metric.equals("fnss")){
				condition = (ssmergeacidentalconfs > 0) /*&& (ssmergerenamingconfs==0) && (ssmergeacidentalconfs > equalconfs)*/;
			}

			if(condition){
				MergedLog mL = mls.find{
					it.mergeCommit.trim() == mergeCommit.trim()
				}
				if(mL != null){
					// create files with merged content
					writeManualAnalysisFile(mL.mergeCommit, mL.mergedFile, mL.BaseContent, '_base',metric);
					writeManualAnalysisFile(mL.mergeCommit, mL.mergedFile, mL.LeftContent, '_left',metric);
					writeManualAnalysisFile(mL.mergeCommit, mL.mergedFile, mL.RightContent,'_right',metric);
					writeManualAnalysisFile(mL.mergeCommit, mL.mergedFile, mL.SemistructuredMergeOutput, '_semistructured',metric);
					writeManualAnalysisFile(mL.mergeCommit, mL.mergedFile, mL.StructuredMergeOutput, '_unstructured',metric);
					
					//getting merge conflicts
					List<MergeConflict> ssconfs = Extractor.extractMergeConflicts(mL.SemistructuredMergeOutput)
					List<MergeConflict> txtconfs= Extractor.extractMergeConflicts(mL.StructuredMergeOutput)

					//fill sheet's entry
					StringBuilder builder = new StringBuilder();
					if(metric.equals("fnss")){
						int numberOfEntries = (textualConf >= ssmergeConf) ? textualConf : ssmergeConf
						for(int i = 0; i < txtconfs.size(); i++){
							builder.append(mL.mergeCommit);
							builder.append(';');
							builder.append(mL.projectName);
							builder.append(';');
							builder.append('https://github.com/spgroup/s3m/blob/master/isi/manualAnalysis_' +metric+'/' + mL.mergeCommit + '/' + mL.mergedFile);
							builder.append(';');
							builder.append('https://github.com/spgroup/s3m/blob/master/isi/manualAnalysis_' +metric+'/' + mL.mergeCommit + '/' + mL.mergedFile + '/' + mL.mergedFile + '_unstructured.java#L' + txtconfs.get(i).startLOC);
							builder.append(';');
							builder.append(';');
							builder.append(';');
							builder.append(';');
							builder.append(';');
							builder.append(';');
							builder.append('\n');
						}
						for(int i = 0; i < ssconfs.size(); i++){
							builder.append(mL.mergeCommit);
							builder.append(';');
							builder.append(mL.projectName);
							builder.append(';');
							builder.append('https://github.com/spgroup/s3m/blob/master/isi/manualAnalysis_' +metric+'/' + mL.mergeCommit + '/' + mL.mergedFile);
							builder.append(';');
							builder.append('https://github.com/spgroup/s3m/blob/master/isi/manualAnalysis_' +metric+'/' + mL.mergeCommit + '/' + mL.mergedFile + '/' + mL.mergedFile + '_semistructured.java#L' + ssconfs.get(i).startLOC);
							builder.append(';');
							builder.append(';');
							builder.append(';');
							builder.append(';');
							builder.append(';');
							builder.append(';');
							builder.append('\n');
						}
					} else {
						builder.append(mL.mergeCommit);
						builder.append(';');
						builder.append(mL.projectName);
						builder.append(';');
						builder.append('https://github.com/spgroup/s3m/blob/master/isi/manualAnalysis_' +metric+'/' + mL.mergeCommit + '/' + mL.mergedFile);
						builder.append(';');
						if(metric.equals("fpss")){
							builder.append("Semistructured Merge");
						} else {
							builder.append("Unstructured Merge");
						}
						builder.append(';');
					}
					sheetLines.add(builder.toString());
					
					println 'Files for scenario ' + mL.mergeCommit + ' generated'
				}
			}
		}
		return sheetLines;
	}

	def private static File writeManualAnalysisFile(String mergecommit, String filename, String content, String suffix, String metric) {
		File mLfile = new File('manualAnalysis_' +metric+'/'+ mergecommit + '/' + filename);
		mLfile.mkdirs();
		mLfile = new File(mLfile.getAbsolutePath() + '/' + filename + suffix + '.java');
		mLfile.append(content);
	}

	def private static logScenarioStatistics(MergeResult result) {
		String logpath = System.getProperty("user.home")+ File.separator + ".jfstmerge" + File.separator;
		File statistics_scenarios = new File(logpath+ "numbers-scenarios.csv");
		if(!statistics_scenarios.exists()){
			def header = 'project;mergecommit;leftcommit;basecommit;rightcommit;ssmergeconfs;ssmergeloc;ssmergerenamingconfs;ssmergedeletionconfs;ssmergeinnerdeletionconfs;ssmergetaeconfs;ssmergenereoconfs;ssmergeinitlblocksconfs;ssmergeacidentalconfs;unmergeconfs;unmergeloc;unmergetime;ssmergetime;unmergeduplicateddeclarationerrors;unmergeorderingconfs;equalconfs\n'
			statistics_scenarios.createNewFile()
			statistics_scenarios.append(header)
		} //ensuring it exists

		def loggermsg = result.commit.projectURL + ';' + result.commit.sha + ';' + result.commit.parent1 + ';' + result.commit.ancestor + ';' + result.commit.parent2 + ';'+ result.ssmergeconfs+ ';' + result.ssmergeloc+ ';' + result.ssmergerenamingconfs+ ';' + result.ssmergedeletionconfs+ ';' + result.ssmergeinnerdeletionconfs+ ';' + result.ssmergetaeconfs+ ';' + result.ssmergenereoconfs+ ';' + result.ssmergeinitlblocksconfs+ ';' + result.ssmergeacidentalconfs+ ';' + result.unmergeconfs+ ';' + result.unmergeloc+ ';' + result.unmergetime+ ';' + result.ssmergetime+ ';' + result.unmergeduplicateddeclarationerrors+ ';' + result.unmergeorderingconfs+ ';' + result.equalconfs
		statistics_scenarios.append(loggermsg+'\n')
	}

	def private static logProjectStatistics(ProjecResult result) {
		File statistics_scenarios = new File("results/numbers-projects.csv");
		if(!statistics_scenarios.exists()){
			def header = 'projectName;numberOfScenarios;conflictingScenarios;ssmergeconfs;ssmergeloc;ssmergerenamingconfs;ssmergedeletionconfs;ssmergeinnerdeletionconfs;ssmergetaeconfs;ssmergenereoconfs;ssmergeinitlblocksconfs;ssmergeacidentalconfs;unmergeconfs;unmergeloc;unmergetime;ssmergetime;unmergeduplicateddeclarationerrors;unmergeorderingconfs;equalconfs;scenariosWithSsmergeConf;scenariosWithTextualConf;scenariosOnlyWithSsmergeConf;scenariosOnlyWithTextualConf;scenariosWithRenamingConfs;scenariosWithDeletionConfs;scenariosWithTaeConfs;scenariosWithNereoConfs;scenariosWithInitBlocksConfs;scenariosWithAcidentalConfs;scenariosWithOrderingConfs;scenariosWithDuplicatedDeclarations'
			statistics_scenarios.append(header+'\n')
			statistics_scenarios.createNewFile()
		} //ensuring it exists

		def loggermsg = result.projectName+ ';' + result.numberOfScenarios+ ';' + result.conflictingScenarios+ ';' + result.ssmergeconfs+ ';' + result.ssmergeloc+ ';' + result.ssmergerenamingconfs+ ';' + result.ssmergedeletionconfs+ ';' + result.ssmergeinnerdeletionconfs+ ';' + result.ssmergetaeconfs+ ';' + result.ssmergenereoconfs+ ';' + result.ssmergeinitlblocksconfs+ ';' + result.ssmergeacidentalconfs+ ';' + result.unmergeconfs+ ';' + result.unmergeloc+ ';' + result.unmergetime+ ';' + result.ssmergetime+ ';' + result.unmergeduplicateddeclarationerrors+ ';' + result.unmergeorderingconfs+ ';' + result.equalconfs+ ';' + result.scenariosWithSsmergeConf+ ';' + result.scenariosWithTextualConf+ ';' + result.scenariosOnlyWithSsmergeConf+ ';' + result.scenariosOnlyWithTextualConf + ';' + result.scenariosWithRenamingConfs+';'+result.scenariosWithDeletionConfs+';'+result.scenariosWithTaeConfs+';'+result.scenariosWithNereoConfs+';'+result.scenariosWithInitBlocksConfs+';'+result.scenariosWithAcidentalConfs+';'+result.scenariosWithOrderingConfs+';'+result.scenariosWithDuplicatedDeclarations
		statistics_scenarios.append(loggermsg+'\n')
	}

	def private static logDivergingScenarios(List<String> divergingScenarios) {
		File diverging_scenarios = new File("results/diverging-scenarios.csv")
		if(!diverging_scenarios.exists()){
			def header = 'project;mergecommit;leftcommit;basecommit;rightcommit;ssmergeconfs;unmergeconfs'
			diverging_scenarios.append(header+'\n')
			diverging_scenarios.createNewFile()
		} //ensuring it exists

		for(String loggermsg : divergingScenarios){
			diverging_scenarios.append(loggermsg+'\n')
		}
	}

	def private static backupGitRepositories(ArrayList<Project> projects){
		projects.each {
			Extractor.init(it,true)
			Extractor.backupRepository(it)
		}
		println('Backup finished!\n')
	}
}