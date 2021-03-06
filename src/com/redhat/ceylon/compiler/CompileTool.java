package com.redhat.ceylon.compiler;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.redhat.ceylon.common.tool.Argument;
import com.redhat.ceylon.common.tool.Description;
import com.redhat.ceylon.common.tool.Option;
import com.redhat.ceylon.common.tool.OptionArgument;
import com.redhat.ceylon.common.tool.Plugin;
import com.redhat.ceylon.common.tool.Rest;
import com.redhat.ceylon.common.tool.RemainingSections;
import com.redhat.ceylon.common.tool.Summary;

@Summary("Compiles Ceylon and Java source code and directly produces module " +
		"and source archives in a module repository.")
@Description("The default module repositories are `modules` and " +
		"http://modules.ceylon-lang.org, and the default source directory is `source`. " +
		"The default output module repository is `modules`." +
		"\n\n" +
		"The `<moduleOrFile>` arguments can be either module names (without versions) " +
		"or file paths specifying the Ceylon or Java source code to compile." +
		"\n\n" +
		"When `<moduleOrFile>` specifies a module the compiler searches for compilation units " +
		"belonging to the specified " +
		"modules in the specified source directories. " +
		"For each specified module, the compiler generates a module archive, " +
		"source archive, and their checksum files in the specified output module " +
		"repository." +
		"\n\n"+
		"When `<moduleOrFile>` specifies a source file just that file is compiled and " +
		"the module archive is created or updated with the .class files produced." +
		"The source file is " +
		"treated as relative to the current directory (so any `--source` " +
		"options are ignored)."+
		"\n\n"+
        "All program elements imported by a compilation unit must belong to the " +
        "same module as the compilation unit, or must belong to a module that " +
        "is explicitly imported in the module descriptor." +
        "\n\n" +
        "The compiler searches for dependencies in the following locations:" +
        "\n\n"+
        "* module archives in the specified repositories,\n"+
        "* source archives in the specified repositories, and\n"+
        "* module directories in the specified source directories.\n")
@RemainingSections(
"## Specifying `javac` options\n" +
"\n"+
"It is possible to pass options to the `javac` compiler by prefixing them " +
"with `--javac=` and separating the javac option from its argument (if any) " +
"using another `=`. For example:\n" +
"\n" +
"* The option `--javac=-target=1.6` is equivalent to `javac`'s `-target 1.6` and,\n" +
"* the option `--javac=-g:none` is equivalent to `javac`'s `-g:none`\n" +
"\n" +
"**Important note**: There is no guarantee that any particular `javac` " +
"option or combination of options will work, or continue to work in " +
"future releases.")
public class CompileTool implements Plugin{

    private List<File> source = Collections.singletonList(new File("source"));
    private File out = new File("modules");
    private List<URI> repo = Collections.emptyList();
    private List<String> module = Collections.emptyList();
    private boolean d;
    private List<String> rest = Collections.emptyList();
    private String user;
    private String pass;
    private boolean verbose = false;

    public CompileTool() {
    }
    
    @OptionArgument(longName="src", argumentName="dir")
    @Description("Specifies a source directory " +
    		"(default: `./source`).")
    public void setSource(List<File> source) {
        this.source = source;
    }
    
    @OptionArgument(longName="repo", argumentName="url")
    @Description("Specifies a module repository containing dependencies. " +
    		"(default: `modules` http://modules.ceylon-lang.org)")
    public void setRepository(List<URI> repo) {
        this.repo = repo;
    }
    
    @Option(longName="d")
    @Description("Disables the default module repositories and source directory.")
    public void setDisableDefaultRepos(boolean d) {
        this.d = d;
    }
    
    @OptionArgument(argumentName="url")
    @Description("Specifies the output module repository (which must be publishable). " +
    		"(default: `./modules`)")
    public void setOut(File out) {
        this.out = out;
    }
    
    @OptionArgument(argumentName="name")
    @Description("Sets the user name for use with an authenticated output repository" +
    		"(no default).")
    public void setUser(String user) {
        this.user = user;
    }
    
    @OptionArgument(argumentName="secret")
    @Description("Sets the password for use with an authenticated output repository" +
    		"(no default).")
    public void setPass(String pass) {
        this.pass = pass;
    }

    @Argument(argumentName="moduleOrFile", multiplicity="+")
    public void setModule(List<String> moduleOrFile) {
        this.module = moduleOrFile;
    }
    
    @Option
    @Description("Produce verbose output.")
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
    
    /** 
     * We collect any other arguments.
     * Long options of the form {@code --javac:<option>}  
     */
    @Rest
    public void setRest(List<String> rest) {
        for (String opt : rest) {
            if (!opt.startsWith("--javac=")) {
                throw new IllegalArgumentException("Unsupported option " + opt);
            }
        }
        this.rest = rest;
    }

    @Override
    public void run() {
        List<String> arguments = new ArrayList<>();
        for (File source : this.source) {
            arguments.add("-src");
            arguments.add(source.getPath());
        }
        
        if (d) {
            arguments.add("-d");
        }
        
        if (verbose) {
            arguments.add("-verbose");
        }
        
        arguments.add("-out");
        arguments.add(out.getPath());
        
        if (user != null) {
            arguments.add("-user");
            arguments.add(user);
        }
        if (pass != null) {
            arguments.add("-pass");
            arguments.add(pass);
        }
        
        for (URI uri : this.repo) {
            arguments.add("-rep");
            arguments.add(uri.toString());
        }
        
        addJavacArguments(arguments);
        
        for (String moduleSpec : this.module) {
            arguments.add(moduleSpec);
        }
        
        System.out.println(arguments);
        System.out.flush();
        com.redhat.ceylon.compiler.java.launcher.Main compiler = new com.redhat.ceylon.compiler.java.launcher.Main("ceylon compile");
        int result = compiler.compile(arguments.toArray(new String[arguments.size()]));
        if (result != 0) {
            throw new RuntimeException("Compilation failed");
        }
    }

    private void addJavacArguments(List<String> arguments) {
        for (String argument : rest) {
            if (argument.startsWith("--javac=")) {
                argument = argument.substring("--javac=".length());
                String value = null;
                int index = argument.indexOf('=');
                if (index != -1) {
                    value = index < argument.length() ? argument.substring(index+1) : "";
                    argument = argument.substring(0, index);
                }
                arguments.add(argument);
                if (value != null) {
                    arguments.add(value);
                }
            }
        }
    }
}
