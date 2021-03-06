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
@nomodel
Integer concreteMethodBySpecificationMethod() {
    return 1;
}
@nomodel
interface ConcreteMethodBySpecification {
    shared formal Integer mFormal(Integer i);
    
    shared Integer mSharedFn() = concreteMethodBySpecificationMethod;
    shared Integer mSharedMem(Integer i = 1) = mFormal;
    
    shared default Integer mDefaultFn() = concreteMethodBySpecificationMethod;
    shared default Integer mDefaultMem(Integer i = 1) = mFormal;
}
@nomodel
interface ConcreteMethodBySpecificationSub satisfies ConcreteMethodBySpecification {
    
    shared Integer mSharedSup(Integer i = 1) = mFormal;
    shared default Integer mDefaultSup(Integer i = 1) = mFormal;
}
@nomodel
class ConcreteMethodBySpecificationImpl() satisfies ConcreteMethodBySpecificationSub {
    shared actual Integer mFormal(Integer i) {
        return i;
    }
}