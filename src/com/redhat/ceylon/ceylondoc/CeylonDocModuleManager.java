/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.redhat.ceylon.ceylondoc;

import java.util.Collections;
import java.util.List;

import com.redhat.ceylon.cmr.api.ArtifactContext;
import com.redhat.ceylon.cmr.api.ArtifactResult;
import com.redhat.ceylon.cmr.api.Logger;
import com.redhat.ceylon.cmr.api.RepositoryManager;
import com.redhat.ceylon.compiler.java.util.Util;
import com.redhat.ceylon.compiler.loader.AbstractModelLoader;
import com.redhat.ceylon.compiler.loader.impl.reflect.ReflectionModelLoader;
import com.redhat.ceylon.compiler.loader.impl.reflect.model.ReflectionModule;
import com.redhat.ceylon.compiler.loader.impl.reflect.model.ReflectionModuleManager;
import com.redhat.ceylon.compiler.loader.mirror.ClassMirror;
import com.redhat.ceylon.compiler.loader.model.LazyPackage;
import com.redhat.ceylon.compiler.typechecker.context.Context;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Modules;
import com.redhat.ceylon.compiler.typechecker.model.Package;

public class CeylonDocModuleManager extends ReflectionModuleManager {

    private List<ModuleSpec> modulesSpecs;
    private Logger log;
    private DocTool tool;

    public CeylonDocModuleManager(DocTool tool, Context context, List<ModuleSpec> modules, Logger log) {
        super(context);
        this.modulesSpecs = modules;
        this.log = log;
        this.tool = tool;
    }

    @Override
    protected boolean isModuleLoadedFromSource(String moduleName) {
        for(ModuleSpec spec : modulesSpecs){
            if(spec.name.equals(moduleName))
                return true;
        }
        return false;
    }
    
    @Override
    protected AbstractModelLoader createModelLoader(Modules modules) {
        return new ReflectionModelLoader(this, modules){
            @Override
            public ClassMirror lookupNewClassMirror(String name) {
                // don't load it from class if we are compiling it
                if(tool.getCompiledClasses().contains(name)){
                    logVerbose("Not loading "+name+" from class because we are typechecking them");
                    return null;
                }
                return super.lookupNewClassMirror(name);
            }
            @Override
            protected void logError(String message) {
                log.error(message);
            }
            @Override
            protected void logVerbose(String message) {
                log.debug(message);
            }
            @Override
            protected void logWarning(String message) {
                log.warning(message);
            }
        };
    }

    @Override
    protected Package createPackage(String pkgName, Module module) {
        final Package pkg = new LazyPackage(getModelLoader());
        List<String> name = pkgName.isEmpty() ? Collections.<String>emptyList() : splitModuleName(pkgName); 
        pkg.setName(name);
        if (module != null) {
            module.getPackages().add(pkg);
            pkg.setModule(module);
        }
        return pkg;
    }

    @Override
    protected Module createModule(List<String> moduleName) {
        Module module = new ReflectionModule(this);
        module.setName(moduleName);
        return module;
    }
    
    @Override
    public void modulesVisited() {
        for(Module module : getContext().getModules().getListOfModules()){
            if(isModuleLoadedFromSource(module.getNameAsString())){
                addOutputModuleToClassPath(module);
            }
        }
    }

    private void addOutputModuleToClassPath(Module module) {
        ArtifactContext ctx = new ArtifactContext(module.getNameAsString(), module.getVersion(), ArtifactContext.CAR);
        ArtifactResult result = getContext().getRepositoryManager().getArtifactResult(ctx);
        if(result != null)
            getModelLoader().addModuleToClassPath(module, result);
    }
}
