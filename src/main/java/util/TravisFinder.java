package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import fr.inria.jtravis.JTravis;
import fr.inria.jtravis.entities.Build;
import fr.inria.jtravis.entities.Builds;
import fr.inria.jtravis.entities.Job;
import fr.inria.jtravis.entities.Repository;

//https://github.com/Spirals-Team/jtravis

public class TravisFinder {
	private static JTravis jTravis =  JTravis.builder().build();

	public static Tuple<String,String,String,Integer> findStatus(String sha, String left, String base, String right, String project) throws InterruptedException{
		//get correponding build and status of the new merge commit		
		Optional<Repository> repository = jTravis.repository().fromSlug(project);
		String status = "NOT FOUND";
		String buildURI = null;
		String jobURI = null;
		Build build = null;
		int attempts = 0;
		if (repository.isPresent()) {
			while(!isFinished(build)){
				System.out.println("Waiting for build " + sha + " ["+(attempts+1)+"]");
				Optional<Builds> optionalBuilds = jTravis.build().fromRepository(repository.get());
				if (optionalBuilds.isPresent()) {
					for (Build b : optionalBuilds.get().getBuilds()) {
						if(b.getCommit().getSha().contains(sha)){
							build = b;
							break;
						}
					}
					if(!isFinished(build)){
						attempts++;
						Thread.sleep(30000);//wait for 30 seconds
					} 
				}
			}
		}
		if(build!= null){
			status = build.getState().name().toUpperCase();
			buildURI = (build.getUri()).replace("build", "builds");
			if(status.equals("ERRORED")){
				List<String> jobsStatus = new ArrayList<String>();
				for(Job job : build.getJobs()){
					String jobState = status;
					String log = getLog(job);
					int jobId =  job.getId();
					jobState = getState(job);

					if(jobState.equals("PASSED")){
						//already "passed"
					} else if(log!=null && isCodeError(log)){
						jobState = "ERRORED";
					} else if(log!=null && isTestError(log)){
						jobState = "FAILED";
					} else {
						jobState = "EXTERNAL";
					}
					jobsStatus.add(jobState + "," + jobId);

					/*	if(log!=null && isCodeError(log)){
						jobState = "ERRORED";
						if(jobState!=null)jobsStatus.add(jobState + "," + jobId);
					} else {
						jobState = getState(job);
						if(jobState.equals("ERRORED")){
							jobState = "EXTERNAL"; //when a job is still errored here, it is external
						} else {
							if(jobState.equals("FAILED")){
								if(isTestError(log)){
									jobState = "FAILED";
								} else {
									jobState = "EXTERNAL";
								}
							}
						}
						jobsStatus.add(jobState + "," + jobId);
					}*/
				}
				for(String st : jobsStatus){
					if(st.contains("FAILED")){
						status = "FAILED";
						jobURI = "/jobs/" + st.split(",")[1];
						break;
					} else if(st.contains("ERRORED")){
						status = "ERRORED";
						jobURI = "/jobs/" + st.split(",")[1];
						break;
					} else if(st.contains("PASSED")){
						status = "PASSED";
						jobURI = "/jobs/" + st.split(",")[1];
						break;
					} 
				}
				if(status.equals("ERRORED") && jobURI == null){
					status = "EXTERNAL";
				}

			} else if(status.equals("FAILED")){
				for(Job firstFailedJob : build.getJobs()){
					String jobState = status;
					String log = getLog(firstFailedJob);
					int jobId =  firstFailedJob.getId();
					jobState = getState(firstFailedJob);
					if(log!=null && jobState.equals("FAILED")){
						if(isTestError(log)){
							status = "FAILED";
							jobURI = "/jobs/" + jobId;
							break;
						} else {
							status = "EXTERNAL";
							break;
						}
					} 
				}
				if(jobURI == null){
					status = "EXTERNAL";
				}
			}

			if(status.equals("FAILED") || status.equals("ERRORED")){
				String parentStatus = isInheritedProblem(left,base,right, project.split("/")[1]); 
				status = (parentStatus != null) ? parentStatus : status;		
			}
		}
		return new Tuple<String, String, String, Integer>(status, buildURI, jobURI,(build!=null)?build.getDuration():0);
	}

	private static String isInheritedProblem(String left, String base, String right, String project) {
		File f = new File("builds-parents.csv");
		if(!f.exists() ) {
			System.out.println("Parents' builds file (builds-parents.csv) does not exist! Ignoring step...");
		} else {
			try(BufferedReader reader = Files.newBufferedReader(Paths.get("builds-parents.csv"))){
				List<Object> lines = reader.lines().collect(Collectors.toList());
				for(int i = 1; i < lines.size(); i++){
					String line = (String)lines.get(i);
					String[] columns = line.split(";");
					String projectName = columns[0].split("/")[1];
					String status  = columns[1];
					String sha = columns[2];
					if(projectName.equals(project)){
						if(sha.startsWith(left) && (status.equals("failed")  || status.equals("errored"))){
							return "INHERITED-LEFT"+"-"+status;
						} else if(sha.startsWith(base) && (status.equals("failed")  || status.equals("errored"))){
							return "INHERITED-BASE"+"-"+status;
						} else if(sha.startsWith(right) && (status.equals("failed")  || status.equals("errored"))){
							return "INHERITED-RIGHT"+"-"+status;
						}
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		return null;
	}

	//	public static Tuple<String,String,Integer> findStatus(String sha, String project) throws InterruptedException{
	//		//get correponding build and status of the new merge commit		
	//		Optional<Repository> repository = jTravis.repository().fromSlug(project);
	//		String status = "NOT FOUND";
	//		String uri = null;
	//		Build build = null;
	//		int attempts = 0;
	//		if (repository.isPresent()) {
	//			while(!isFinished(build)){
	//				System.out.println("Waiting for build " + sha + " ["+(attempts+1)+"]");
	//				Optional<Builds> optionalBuilds = jTravis.build().fromRepository(repository.get());
	//				if (optionalBuilds.isPresent()) {
	//					for (Build b : optionalBuilds.get().getBuilds()) {
	//						if(b.getCommit().getSha().contains(sha)){
	//							build = b;
	//							break;
	//						}
	//					}
	//					if(!isFinished(build)){
	//						attempts++;
	//						Thread.sleep(30000);//wait for 30 seconds
	//					} 
	//				}
	//			}
	//		}
	//		if(build!= null){
	//			status = build.getState().name().toUpperCase();
	//			uri = (build.getUri()).replace("build", "builds");
	//			if(status.equals("ERRORED")){
	//				List<String> jobsStatus = new ArrayList<String>();
	//				for(Job job : build.getJobs()){
	//					String log = getLog(job);
	//					if(log!=null && isExternalError(log)){
	//						status = "EXTERNAL";
	//						jobsStatus.add(status);
	//					} else {
	//						String jobState = getState(job);
	//						if(jobState!=null)jobsStatus.add(jobState);
	//					}
	//				}
	//				String allstatus = jobsStatus.toString();
	//				if(allstatus.contains("FAILED")){
	//					status = "FAILED";
	//				} else if(allstatus.contains("ERRORED")){
	//					status = "ERRORED";
	//				} else if(allstatus.contains("PASSED")){
	//					status = "PASSED";
	//				} else{}
	//			}
	//		}
	//		return new Tuple<String, String, Integer>(status,uri,(build!=null)?build.getDuration():0);
	//	}

	private static boolean isFinished(Build build) {
		return build!=null && (build.getState().name().toUpperCase().equals("PASSED") || build.getState().name().toUpperCase().equals("ERRORED") 
				|| build.getState().name().toUpperCase().equals("FAILED") || build.getState().name().toUpperCase().equals("CANCELED"));
	}

	private static String getLog(Job job){
		String log = null;
		BufferedReader reader = null;
		try{
			System.out.println("Reading "+job.getUri()+" log...");
			ProcessBuilder command = new ProcessBuilder("curl","-X","GET","https://api.travis-ci.org/v3/job/"+job.getId()+"/log.txt");
			Process process = command.start();
			log="";
			StringBuilder sb = new StringBuilder();
			reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line = null;
			while ((line = reader.readLine()) != null) {
				sb.append(line + System.getProperty("line.separator"));
			}
			log=sb.toString();
		}catch(Exception e){
			return null;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return log;
	}

	private static String getState(Job job) {
		String state = null;
		BufferedReader reader = null;
		try{
			System.out.println("Reading "+job.getUri()+" state...");
			ProcessBuilder command = new ProcessBuilder("curl","-X","GET","https://api.travis-ci.org/v3/job/"+job.getId());
			Process process = command.start();
			reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			state="";
			String line;
			StringBuilder sb = new StringBuilder();
			while ((line = reader.readLine()) != null) {
				sb.append(line + System.getProperty("line.separator"));
			}
			state=sb.toString();
			JSONObject jsonObj = new JSONObject(state);
			state = jsonObj.getString("state").toUpperCase();
		}catch(Exception e){
			return null;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return state;
	}

	private static boolean isCodeError(String log) {
		String copyLog =log.replaceAll("\\r\\n|\\r|\\n","");
		if (       copyLog.matches(".*ERROR.*actual and formal argument lists differ in length.*") 
				|| copyLog.matches(".*ERROR.*no suitable method found for.*") 
				|| copyLog.matches(".*ERROR.*cannot be applied to.*") 
				|| copyLog.matches(".*ERROR.*has private access in.*") 
				|| copyLog.matches(".*ERROR.*is a subclass of alternative.*") 
				|| copyLog.matches(".*ERROR.*COMPILATION ERROR.*java.*cannot be converted to.*") 
				|| copyLog.matches(".*cannot return a value from method whose result type is void.*") 
				|| copyLog.matches(".*incompatible types.*")
				|| copyLog.matches(".*ERROR.*error: incompatible types:.*cannot be converted to.*")
				|| copyLog.matches(".*ERROR.*does not override.*method.*")
				|| copyLog.matches(".*Alternatives in a multi-catch statement cannot be related by subclassing.*")
				|| copyLog.matches(".*javac.*cannot find symbol.*") 
				|| copyLog.matches(".*ERROR.*cannot find symbol.*") 
				|| copyLog.matches(".*ERROR.*cannot find symbol.*symbol.*method.*class.*") 
				|| copyLog.matches(".*ERROR.*not find: type.*") 
				|| copyLog.matches(".*ERROR.*is not a member of.*")
				//|| copyLog.matches(".*["+stringErro+"].*"+stringNoOverride+".*["+stringErro+"].*")
				//|| copyLog.matches(".*[javac].*cannot find symbol.*[javac].*(location:)+.*") 
				//|| copyLog.matches(".*javac.*cannot find symbol.*location.*") 
				){
			return true;
		}
		return false;
	}

	private static boolean isTestError(String log) {
		String copyLog =log.replaceAll("\\r\\n|\\r|\\n","");
		if (       copyLog.matches(".*There are test failures.*") 
				|| copyLog.matches(".*There was test failures.*") 
				|| copyLog.matches(".*Build failed to meet Clover coverage targets: The following coverage targets for null were not met.*") 
				|| copyLog.matches(".*Failed tests:.*Tests run:.*Failures: [1-9][0-9]*.*BUILD FAILURE.*") 
				|| copyLog.matches(".*Failed tests:.*Tests run:.*") 
				|| copyLog.matches(".*Failures: [1-9][0-9]*.*") 
				){
			return true;
		}
		return false;
	}

	private static boolean isExternalError(String log) {
		String copyLog =log.replaceAll("\\r\\n|\\r|\\n","");

		//external errored causes
		String stringUndefinedExt = "uses an undefined extension point";
		String stringErro = "ERROR";
		String stringDependency = "Could not resolve dependencies for project";
		String stringProblemScript = "A problem occurred evaluating script";
		String stringAddTask = "Cannot add task";
		String stringTaskExists = "as a task with that name already exists";
		String stringBuildFail = "BUILD FAILED";
		String stringNonParseable = "Non-parseable POM";
		String stringScript = "Script";
		String stringGradle = ".gradle";
		String stringTimeout = "No output has been received in the last";
		String stringValidVersion = " must be a valid version";
		String stringMissing = " failed: A required class was missing while executing";
		String stringErrorProcessing = "dpkg: error processing ";
		String stringUnsupported = "Unsupported major.minor version";
		String stringFailedGoal = "Failed to execute goal";
		String stringNotResolvedDep = "or one of its dependencies could not be resolved:";
		String stringFailedCollect = "Failed to collect dependencies";
		String stringConnectionReset = "Connection reset";
		String stringNotDefinedProp = "Your user name and password are not defined. Ask your database administrator to set up";
		String stringBuildsFailed = "builds failed";
		String stringNoMaintained = "no longer maintained";
		String stringElement = "Element";
		String stringNoExist = "does not exist";
		String stringStopped = "Your build has been stopped";

		if (
				copyLog.matches(".*The JAVA_HOME environment variable is not defined correctly.*")
				|| copyLog.matches(".*Could not transfer artifact.*")
				|| copyLog.matches(".*"+stringBuildFail+".*"+stringUndefinedExt+".*")
				|| copyLog.matches(".*"+stringErro+".*"+stringDependency+".*")
				//|| copyLog.matches(".*"+stringErro+".*"+stringNonParseable+".*("+stringUnexpected+".*"+stringErro+")?.*")
				|| copyLog.matches(".*"+stringErro+".*"+stringNonParseable+".*")
				|| copyLog.matches(".*"+stringScript+".*"+stringGradle+".*"+stringProblemScript+".*"+stringAddTask+".*"+stringTaskExists+".*"+ stringBuildFail+".*")
				|| copyLog.matches(".*"+stringTimeout+".*")
				|| copyLog.matches(".*404 Not Found.*")
				|| copyLog.matches(".*The job exceeded the maximum time limit for jobs, and has been terminated.*")
				|| copyLog.matches(".*error: device not found.*")
				|| copyLog.matches(".*ValueError: No JSON object could be decoded.*")
				|| copyLog.matches(".*The job has been terminated.*")
				|| copyLog.matches(".*test run exceeded.*")
				|| copyLog.matches(".*The log length has exceeded the limit of 4 Megabytes.*")
				|| copyLog.matches(".*ERROR 503: Service Unavailable.*")
				|| copyLog.matches(".*ERROR.*Failed to execute goal.*Fatal error compiling: invalid.*")
				|| copyLog.matches(".*ERROR.*Failed to execute goal.*Fatal error compiling.*")
				|| copyLog.matches(".*ERROR.*Failed to execute goal.*There.*error.*")
				|| copyLog.matches(".*error reading input file:.*")
				|| copyLog.matches(".*"+stringErro+".*deprecated.*"+stringNoMaintained+".*")
				|| copyLog.matches(".*Make sure your network and proxy settings are correct.*")
				|| copyLog.matches(".*"+stringFailedGoal+".*"+stringBuildsFailed+".*")
				|| copyLog.matches(".*"+stringNotDefinedProp+".*")
				|| copyLog.matches(".*"+stringFailedGoal+".*"+stringNotResolvedDep+"["+stringFailedCollect+"]?.*["+stringConnectionReset+"]?.*")
				|| copyLog.matches(".*"+stringUnsupported+".*"+stringStopped+".*")
				|| copyLog.matches(".*"+stringErrorProcessing+".*")
				|| copyLog.matches(".*Non-resolvable parent POM:.*")
				|| copyLog.matches(".*Failure to find|Could not find artifact.*")
				|| copyLog.matches(".*"+stringErro+".*"+stringMissing+".*")
				|| copyLog.matches(".*"+stringErro+".*"+stringValidVersion+".*")
				|| copyLog.matches(".*"+stringElement+".*"+stringNoExist+".*") 
				|| copyLog.matches(".*No output has been received in the last.*")
				|| copyLog.matches(".*error: device not found.*")
				|| copyLog.matches(".*The command \"git.*failed.*")
				){
			return true;
		}
		return false;
	}

	public static void main(String[] args) {
		try {
			TravisFinder.findStatus("271cc3a5",null,null,null, "guilhermejccavalcanti/LittleProxy");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//fixERROREDStatus();
		//fillJOBSuri();
		//fixFAILEDStatus();

		//		try {
		//			findStatus("c2c647f", "guilhermejccavalcanti/neo4j-reco");
		//		} catch (InterruptedException e) {
		//			// TODO Auto-generated catch block
		//			e.printStackTrace();
		//		}
	}

	/*
	 * SCRIPTS BELOW
	 * for post-execution only, 
	 * only if you want to fix something wrong on the results of the original execution
	 */
	public static void fixERROREDStatus(){
		try {
			StringBuilder builder = new StringBuilder();
			BufferedReader reader;
			reader = Files.newBufferedReader(Paths.get("numbers-scenarios.csv"));
			List<Object> results = reader.lines().collect(Collectors.toList());
			for(int i = 1; i < results.size(); i++){
				String r = (String) results.get(i);
				String[] colunas = r.split(";");
				String buildstate = colunas[22];
				if(buildstate.equals("EXTERNAL") || buildstate.equals("ERRORED")){
					String buildid = (colunas[23].split("/"))[2];
					List<String> jobsStatus = new ArrayList<String>();
					String status = buildstate;
					JSONArray jobs = getJobs(buildid);
					for (int j = 0; j < jobs.length(); j++) {		
						JSONObject job = jobs.getJSONObject(j);
						int id = (Integer) job.get("id");
						String log = getLog(id);
						String jobState = status;
						if(log!=null && isCodeError(log)){
							jobState = getState(id);
							if(jobState!=null)jobsStatus.add(jobState);
						} else {
							jobState = "EXTERNAL";
							jobsStatus.add(jobState);
						}
					}
					String allstatus = jobsStatus.toString();
					if(allstatus.contains("FAILED")){
						status = "FAILED";
					} else if(allstatus.contains("ERRORED")){
						status = "ERRORED";
					} else if(allstatus.contains("PASSED")){
						status = "PASSED";
					} else{
						status = "EXTERNAL";
					}
					r = r.replaceAll(buildstate, status);
				}
				builder.append(r+"\n");
			}
			BufferedWriter writer = new BufferedWriter(new FileWriter("numbers-scenarios-fix.csv"));
			writer.write(builder.toString());
			writer.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void fixFAILEDStatus(){
		try {
			StringBuilder builder = new StringBuilder();
			BufferedReader reader;
			reader = Files.newBufferedReader(Paths.get("numbers-scenarios.csv"));
			List<Object> results = reader.lines().collect(Collectors.toList());
			for(int i = 1; i < results.size(); i++){
				String r = (String) results.get(i);
				String[] colunas = r.split(";");
				String buildstate = colunas[22];
				if(buildstate.equals("FAILED")){
					String buildid = (colunas[23].split("/"))[2];
					String status = buildstate;
					JSONArray jobs = getJobs(buildid);
					for (int j = 0; j < jobs.length(); j++) {		
						JSONObject job = jobs.getJSONObject(j);
						int id = (Integer) job.get("id");
						String log = getLog(id);
						String jobState = status;
						jobState = getState(id);
						if(log != null && jobState.equals("FAILED")){
							if(isTestError(log)){
								status = "FAILED";
								break;
							} else {
								status = "EXTERNAL";
								break;
							}
						}
					}
					r = r.replaceAll(buildstate, status);
				}
				builder.append(r+"\n");
			}
			BufferedWriter writer = new BufferedWriter(new FileWriter("numbers-scenarios-fix.csv"));
			writer.write(builder.toString());
			writer.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void fillJOBSuri(){
		try {
			StringBuilder builder = new StringBuilder();
			BufferedReader reader;
			reader = Files.newBufferedReader(Paths.get("numbers-scenarios.csv"));
			List<Object> results = reader.lines().collect(Collectors.toList());
			for(int i = 1; i < results.size(); i++){
				String r = (String) results.get(i);
				String[] colunas = r.split(";");
				String buildstate = colunas[22];
				if(	   buildstate.equals("ERRORED")
						|| buildstate.equals("PASSED")
						|| buildstate.equals("FAILED")
						){
					String buildid = (colunas[23].split("/"))[2];
					List<String> jobsStatus = new ArrayList<String>();
					String status = buildstate;
					JSONArray jobs = getJobs(buildid);
					for (int j = 0; j < jobs.length(); j++) {		
						JSONObject job = jobs.getJSONObject(j);
						int id = (Integer) job.get("id");
						String log = getLog(id);
						String jobState = status;
						if(buildstate.equals("ERRORED")){
							if(log!=null && isCodeError(log)){
								jobState = getState(id);
								if(jobState!=null)jobsStatus.add(jobState+","+id);
							} 
						} else {
							if(log!=null){
								jobState = getState(id);
								if(jobState.equals(buildstate)){
									if(jobState!=null)jobsStatus.add(jobState+","+id);	
								}
							}
						}
					}
					String jobURI = null;
					for(String st : jobsStatus){
						if(st.contains("FAILED")){
							status = "FAILED";
							jobURI = "/jobs/" + st.split(",")[1];
							break;
						} else if(st.contains("ERRORED")){
							status = "ERRORED";
							jobURI = "/jobs/" + st.split(",")[1];
							break;
						} else if(st.contains("PASSED")){
							status = "PASSED";
							jobURI = "/jobs/" + st.split(",")[1];
							break;
						} 
					}
					r = r.replaceAll(buildstate, status);
					r = r + ";" + jobURI;
				}
				builder.append(r+"\n");
			}
			BufferedWriter writer = new BufferedWriter(new FileWriter("numbers-scenarios-fix.csv"));
			writer.write(builder.toString());
			writer.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	private static JSONArray getJobs(String buildid) {
		JSONArray jobs = null;
		BufferedReader reader2 = null;
		try{
			System.out.println("Reading "+buildid+" jobs...");
			ProcessBuilder command = new ProcessBuilder("curl","-X","GET","https://api.travis-ci.org/v3/build/"+buildid);
			Process process = command.start();
			reader2 = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			StringBuilder sb = new StringBuilder();
			while ((line = reader2.readLine()) != null) {
				sb.append(line + System.getProperty("line.separator"));
			}
			sb.toString();
			JSONObject jsonObj = new JSONObject(sb.toString());
			jobs = jsonObj.getJSONArray("jobs");
		}catch(Exception e){
			e.printStackTrace();
		} finally {
			if (reader2 != null) {
				try {
					reader2.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return jobs;
	}

	private static String getLog(int jobid){
		String log = null;
		BufferedReader reader = null;
		try{
			System.out.println("Reading job #"+jobid+" log...");
			ProcessBuilder command = new ProcessBuilder("curl","-X","GET","https://api.travis-ci.org/v3/job/"+jobid+"/log.txt");
			Process process = command.start();
			log="";
			StringBuilder sb = new StringBuilder();
			reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line = null;
			while ((line = reader.readLine()) != null) {
				sb.append(line + System.getProperty("line.separator"));
			}
			log=sb.toString();
		}catch(Exception e){
			return null;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return log;
	}

	private static String getState(int jobid) {
		String state = null;
		BufferedReader reader = null;
		try{
			System.out.println("Reading job #"+jobid+" state...");
			ProcessBuilder command = new ProcessBuilder("curl","-X","GET","https://api.travis-ci.org/v3/job/"+jobid);
			Process process = command.start();
			reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			state="";
			String line;
			StringBuilder sb = new StringBuilder();
			while ((line = reader.readLine()) != null) {
				sb.append(line + System.getProperty("line.separator"));
			}
			state=sb.toString();
			JSONObject jsonObj = new JSONObject(state);
			state = jsonObj.getString("state").toUpperCase();
		}catch(Exception e){
			return null;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return state;
	}
}
