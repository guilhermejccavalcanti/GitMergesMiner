package util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

/**
 * This class is usefull to create the R scripts. It uses the files from "templates" folder. 
 * @author Guilherme
 */
@SuppressWarnings({ "rawtypes", "unchecked", "resource" })
public class Velocity {
	public static void main(String[] args) throws Exception {
		/*  first, get and initialize an engine  */
		VelocityEngine ve = new VelocityEngine();
		ve.init();
		/*  organize our data  */
		ArrayList list = new ArrayList();
		FileInputStream stream = new FileInputStream("templates/project_list_velocity.txt");
		InputStreamReader reader = new InputStreamReader(stream);
		BufferedReader br = new BufferedReader(reader);
		String line = br.readLine();
		while(line != null){
			String folder 	= line.split(",")[0];
			String name 	= line.split(",")[1];
			String language = line.split(",")[2];
			Map map = new HashMap();
			map.put("name", name);
			map.put("folder", folder);
			map.put("language", language);
			list.add(map);
			line = br.readLine();
		}

		/*  add that list to a VelocityContext  */
		VelocityContext context = new VelocityContext();
		context.put("projectList", list);
		/*  get the Template  */
		Template t = ve.getTemplate("templates/template_Percentage.vm");
		/*  now render the template into a Writer  */
		StringWriter writer = new StringWriter();
		t.merge( context, writer ); 
		PrintWriter out = new PrintWriter("templates/Percentages.r");
		out.println(writer.toString());
		out.close();
		
		
		context = new VelocityContext();
		context.put("projectList", list);
		t = ve.getTemplate("templates/template_Diagrams.vm");
		writer = new StringWriter();
		t.merge( context, writer ); 
		out = new PrintWriter("templates/Diagrams.r");
		out.println(writer.toString());
		out.close();
		
		System.out.println("R scripts created.");
	}
}
