/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 * 
 * Copyright @2010-2011 the original author or authors.
 */
package org.assertj.assertions.generator.util;

import static java.lang.Character.isUpperCase;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.uncapitalize;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.assertj.assertions.generator.description.TypeName;
import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * 
 * Some utilities methods related to classes and packages.
 * 
 * @author William Delanoue
 * 
 */
public class PsiClassUtil {
  private static final Logger logger = Logger.getInstance(PsiClassUtil.class);

  private static final String CLASS_SUFFIX = ".class";
  public static final String IS_PREFIX = "is";
  public static final String GET_PREFIX = "get";

  @NotNull
  private final Project project;

  @NotNull
  private final JavaPsiFacade javaPsiFacade;

  public PsiClassUtil(@NotNull Project project) {
    this.project = project;
    this.javaPsiFacade = JavaPsiFacade.getInstance(project);
  }

  /**
   * Returns the property name of given getter method, examples :
   * 
   * <pre>
   * getName -> name
   * </pre>
   * 
   * <pre>
   * isMostValuablePlayer -> mostValuablePlayer
   * </pre>
   * 
   * @param getter getter method to deduce property from.
   * @return the property name of given getter method
   */
  public static String propertyNameOf(PsiMethod getter) {
    String prefixToRemove = isBooleanGetter(getter) ? IS_PREFIX : GET_PREFIX;
    String propertyWithCapitalLetter = substringAfter(getter.getName(), prefixToRemove);
    return uncapitalize(propertyWithCapitalLetter);
  }

  public TypeName getTypeName(PsiClass clazz) {
    PsiJavaFile file = (PsiJavaFile) clazz.getContainingFile();
    PsiPackage pkg = javaPsiFacade.findPackage(file.getPackageName());
    return new TypeName(clazz.getName(), pkg.getName());
  }

  public boolean isIterable(PsiType type) {
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType) type;
      PsiClass clazz = classType.resolve();
      PsiClass iterable = javaPsiFacade.findClass(Iterable.class.getName(), GlobalSearchScope.allScope(project));
      return clazz.isInheritor(iterable, true);
    }
    return false;
  }

  public static boolean isStandardGetter(PsiMethod method) {
    return isValidGetterName(method.getName())
        && !"void".equalsIgnoreCase(method.getReturnType().getInternalCanonicalText())
        && method.getParameterList().getParameters().length == 0;
  }

  public static boolean isBooleanGetter(PsiMethod method) {
    return isValidBooleanGetterName(method.getName())
        && "boolean".equals(method.getReturnType().getInternalCanonicalText())
        && method.getParameterList().getParameters().length == 0;
  }

  public static boolean isValidGetterName(String methodName) {
    return isValidStandardGetterName(methodName) || isValidBooleanGetterName(methodName);
  }

  private static boolean isValidStandardGetterName(String name) {
    return name.length() >= GET_PREFIX.length() + 1 && isUpperCase(name.charAt(GET_PREFIX.length()))
        && name.startsWith(GET_PREFIX);
  }

  private static boolean isValidBooleanGetterName(String name) {
    return name.length() >= IS_PREFIX.length() + 1 && isUpperCase(name.charAt(IS_PREFIX.length()))
        && name.startsWith(IS_PREFIX);
  }

  public static List<PsiMethod> getterMethodsOf(PsiClass clazz) {
    PsiMethod[] methods = clazz.getMethods();
    List<PsiMethod> getters = new ArrayList<PsiMethod>();
    for (int i = 0; i < methods.length; i++) {
      PsiMethod method = methods[i];
      if (isNotDefinedInObjectClass(method) && (isStandardGetter(method) || isBooleanGetter(method))) {
        getters.add(method);
      }
    }
    return getters;
  }

  private static boolean isNotDefinedInObjectClass(PsiMethod method) {
    return method.getReturnType() != null;
  }

  public boolean isArray(PsiType propertyType) {
    return propertyType instanceof PsiArrayType;
  }

  public TypeName getTypeName(PsiType type) {
    if (type instanceof PsiPrimitiveType) {
      return new TypeName(type.getCanonicalText());
    }
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType) type;
      return new TypeName(classType.getCanonicalText());
    }
    return new TypeName(type.getCanonicalText());
  }
}
