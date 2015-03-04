/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.asual.lesscss.compiler;

import com.asual.lesscss.LessException;
import com.asual.lesscss.LessOptions;
import com.asual.lesscss.loader.ResourceLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dynjs.Config;
import org.dynjs.Config.CompileMode;
import org.dynjs.compiler.JSCompiler;
import org.dynjs.parser.ast.ProgramTree;
import org.dynjs.parser.js.JavascriptParser;
import org.dynjs.runtime.DynJS;
import org.dynjs.runtime.ExecutionContext;
import org.dynjs.runtime.GlobalObject;
import org.dynjs.runtime.JSFunction;
import org.dynjs.runtime.JSProgram;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;

/**
 * LessCompiler implementation using Dynjs instead of Rhino.
 * <p>Author: <a href="http://gplus.to/tzrlk">Peter Cummuskey</a></p>
 */
public class DynjsCompiler implements LessCompiler {

	private final Log logger = LogFactory.getLog(getClass());
	private final DynJS dynjs;
	private ExecutionContext context;
	private final JSFunction compile;

	public DynjsCompiler(LessOptions options, ResourceLoader loader, URL less, URL env, URL engine, URL cssmin,
	                     URL sourceMap) throws IOException {

		Config config = new Config();
		config.setCompileMode(CompileMode.FORCE);

		dynjs = new DynJS(config);

		final JSCompiler compiler = dynjs.getCompiler();
		context = dynjs.getDefaultExecutionContext();
		final JavascriptParser parser = new JavascriptParser(context);


		execute(compiler, parser, sourceMap);
		execute(compiler, parser, env);

		final GlobalObject globals = context.getGlobalObject();
		globals.defineGlobalProperty("charset", options.getCharset());
		globals.defineGlobalProperty("css", options.isCss());
		globals.defineGlobalProperty("lineNumbers", options.getLineNumbers());
		globals.defineGlobalProperty("optimization", options.getOptimization());
		globals.defineGlobalProperty("sourceMap", options.isSourceMap());
		globals.defineGlobalProperty("sourceMapRootpath", options.getSourceMapRootpath());
		globals.defineGlobalProperty("sourceMapBasepath", options.getSourceMapBasepath());
		globals.defineGlobalProperty("sourceMapURL", options.getSourceMapUrl());

		if (options.getPaths() != null) {
			globals.defineGlobalProperty("paths", options.getPaths());
		}

		execute(compiler, parser, less);
		execute(compiler, parser, cssmin);
		execute(compiler, parser, engine);

		compile = (JSFunction) context.resolve("compile").getValue(context);

	}

	private void execute(JSCompiler compiler, JavascriptParser parser, URL resource)
			throws IOException {
		Reader reader = null;
		try {
			reader = new InputStreamReader(resource.openConnection().getInputStream());
			final ProgramTree tree = parser.parse(reader);
			final JSProgram program = compiler.compileProgram(context, tree, false);
			context = context.createEvalExecutionContext(program, false);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException error) {
					logger.warn("Unable to close stream", error);
				}
			}
		}
	}

	@Override
	public String compile(String input, String location, boolean compress) throws LessException {
		try {
			return (String) dynjs.getDefaultExecutionContext()
					.call(compile, (Object) null, input, location, compress);
		} catch (Exception error) {
			throw new LessException(error);
		}
	}

}
