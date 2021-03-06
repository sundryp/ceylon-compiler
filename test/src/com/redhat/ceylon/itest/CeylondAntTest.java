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
package com.redhat.ceylon.itest;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

public class CeylondAntTest extends AntBasedTest {

    public CeylondAntTest() throws Exception {
        super("test/src/com/redhat/ceylon/itest/ceylond-ant.xml");
    }
    
    @Test
    public void testDocumentModuleFooBadExecutable() throws Exception {
        System.setProperty("script.ceylond", "some/nonsense/path");
        AntResult result = ant("foo-alone");
        Assert.assertEquals(1, result.getStatusCode());
        assertContains(result.getStdout(), "Failed to find 'ceylond' executable in some/nonsense/path");
    }
    
    @Test
    public void testDocumentModuleFoo() throws Exception {
        AntResult result = ant("foo-alone");
        Assert.assertEquals(0, result.getStatusCode());
        Assert.assertTrue(new File(result.getOut(), "com/example/foo/1.0/module-doc/a/index.html").exists());
        Assert.assertEquals(1, new File(result.getOut(), "com/example").list().length);
    }
    
    @Test
    public void testDocumentModuleFooTwice() throws Exception {
        AntResult result = ant("foo-alone");
        Assert.assertEquals(0, result.getStatusCode());
        File index = new File(result.getOut(), "com/example/foo/1.0/module-doc/a/index.html");
        Assert.assertTrue(index.exists());
        Assert.assertEquals(1, new File(result.getOut(), "com/example").list().length);
        assertNotContains(result.getStdout(), "[ceylond] No need to compile com.example.foo, it's up to date");
        assertNotContains(result.getStdout(), "[ceylond] Everything's up to date");
        final long lastModified = index.lastModified();
        
        result = ant("foo-alone");
        Assert.assertEquals(0, result.getStatusCode());
        Assert.assertTrue(index.exists());
        assertContains(result.getStdout(), "[ceylond] No need to compile com.example.foo, it's up to date");
        assertContains(result.getStdout(), "[ceylond] Everything's up to date");
        Assert.assertEquals(lastModified, index.lastModified());
    }
    
    @Test
    public void testDocumentModuleFooAndBarTogether() throws Exception {
        AntResult result = ant("foo-and-bar");
        Assert.assertEquals(0, result.getStatusCode());
        Assert.assertTrue(new File(result.getOut(), "com/example/foo/1.0/module-doc/a/index.html").exists());
        Assert.assertTrue(new File(result.getOut(), "com/example/bar/1.0/module-doc/b/index.html").exists());
    }

}
