package main.java.utils;

import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Properties;

public class Props {
//funcao da lab1
	public static void load( String resourceFile ) {
		try( var in = Props.class.getClassLoader().getResourceAsStream(resourceFile) ) {
			var reader = new InputStreamReader(in);
			var props = new Properties();
			props.load(reader);
			props.forEach( (k,v) -> System.setProperty(k.toString(), v.toString()));
			System.getenv().forEach( System::setProperty );
		}
		catch( Exception x  ) {
			x.printStackTrace();
		}

	}
	/* esta funcao ja aqui estava
	public static void load(String[] keyValuePairs) {
		System.out.println(Arrays.asList( keyValuePairs));
		for( var pair: keyValuePairs ) {
			var parts = pair.split("=");
			if( parts.length == 2) 
				System.setProperty(parts[0], parts[1]);
		}
	}*/
}
