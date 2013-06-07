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
import static java.lang.reflect.Modifier.isPublic;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.uncapitalize;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
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
   * Get <b>public</b> classes in given directory (recursively).
   * <p>
   * Note that <b>anonymous</b> and <b>local</b> classes are excluded from the resulting list.
   * 
   * @param directory directory where to look for classes
   * @param packageName package name corresponding to directory
   * @param classLoader used classloader
   * @return
   * @throws java.io.UnsupportedEncodingException
   */
  private static List<Class<?>> getClassesInDirectory(File directory, String packageName, ClassLoader classLoader)
      throws UnsupportedEncodingException {
    List<Class<?>> classes = new ArrayList<Class<?>>();
    // Capture all the .class files in this directory
    // Get the list of the files contained in the package
    File[] files = directory.listFiles();
    for (File currentFile : files) {
      String currentFileName = currentFile.getName();
      if (isClass(currentFileName)) {
        // CHECKSTYLE:OFF
        try {
          // removes the .class extension
          String className = packageName + '.' + StringUtils.remove(currentFileName, CLASS_SUFFIX);
          Class<?> loadedClass = loadClass(className, classLoader);
          // we are only interested in public classes that are neither anonymous nor local
          if (isClassCandidateToAssertionsGeneration(loadedClass)) {
            classes.add(loadedClass);
          }
        } catch (Throwable e) {
          // do nothing. this class hasn't been found by the loader, and we don't care.
        }
        // CHECKSTYLE:ON
      } else if (currentFile.isDirectory()) {
        // It's another package
        String subPackageName = packageName + ClassUtils.PACKAGE_SEPARATOR + currentFileName;
        // Ask for all resources for the path
        URL resource = classLoader.getResource(subPackageName.replace('.', File.separatorChar));
        File subDirectory = new File(URLDecoder.decode(resource.getPath(), "UTF-8"));
        List<Class<?>> classesForSubPackage = getClassesInDirectory(subDirectory, subPackageName, classLoader);
        classes.addAll(classesForSubPackage);
      }
    }
    return classes;
  }

  /**
   * @param loadedClass
   * @return
   */
  private static boolean isClassCandidateToAssertionsGeneration(Class<?> loadedClass) {
    return loadedClass != null && isPublic(loadedClass.getModifiers()) && !loadedClass.isAnonymousClass()
        && !loadedClass.isLocalClass();
  }

  private static boolean isClass(String fileName) {
    return fileName.endsWith(CLASS_SUFFIX);
  }

  private static Class<?> loadClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
    return Class.forName(className, true, classLoader);
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
    // return Iterable.class.isAssignableFrom(returnType);
  }

  public boolean isArray(PsiClass returnType) {
    PsiClass iterable = JavaPsiFacade.getInstance(null).findClass(Iterable.class.getName(),
        GlobalSearchScope.allScope(project));
    return returnType.isInheritor(iterable, true);
    // return Iterable.class.isAssignableFrom(returnType);
  }

  public static boolean isStandardGetter(PsiMethod method) {
    return isValidStandardGetterName(method.getName()) && !Void.TYPE.equals(method.getReturnType())
        && method.getParameterList().getParameters().length == 0;
  }

  public static boolean isBooleanGetter(PsiMethod method) {
    return isValidBooleanGetterName(method.getName()) && Boolean.TYPE.equals(method.getReturnType())
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
    // TODO ?!
    return !method.getContainingClass().equals(Object.class);
  }

  public static Set<Class<?>> getClassesRelatedTo(Type type) {
    Set<Class<?>> classes = new HashSet<Class<?>>();

    // non generic type : just add current type.
    if (type instanceof Class) {
      classes.add((Class<?>) type);
      return classes;
    }

    // generic type : add current type and its parameter types
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      for (Type actualTypeArgument : parameterizedType.getActualTypeArguments()) {
        if (actualTypeArgument instanceof ParameterizedType) {
          classes.addAll(getClassesRelatedTo(actualTypeArgument));
        } else if (actualTypeArgument instanceof Class) {
          classes.add((Class<?>) actualTypeArgument);
        } else if (actualTypeArgument instanceof GenericArrayType) {
          classes.addAll(getClassesRelatedTo(actualTypeArgument));
        }
        // throw new IllegalArgumentException("cannot find type " + actualTypeArgument);
        // I'm almost sure we should not arrive here !
      }
      Type rawType = parameterizedType.getRawType();
      if (rawType instanceof Class) {
        classes.add((Class<?>) rawType);
      }
    }
    return classes;
  }

  public boolean isArray(PsiType propertyType) {
    return propertyType instanceof PsiArrayType;
  }

  //
  // public boolean isIterable(PsiType type) {
  //
  // // ClassInheritorsSearch.
  // if (type instanceof PsiClassType) {
  // PsiClassType classType = (PsiClassType) type;
  // PsiClass iterable = javaPsiFacade.findClass(Iterable.class.getName(), GlobalSearchScope.allScope(project));
  // logger.info("iterable : " + iterable);
  // logger.info("classType : " + classType);
  // for (PsiType psiType : iterable.getExtendsListTypes()) {
  // logger.info("psiType : " + psiType + " isAssignableFrom : " + classType.isAssignableFrom(psiType));
  // }
  // }
  // return false; // To change body of created methods use File | Settings | File Templates.
  // }

  public TypeName getTypeName(PsiType type) {
    if (type instanceof PsiPrimitiveType) {
      return new TypeName(type.getCanonicalText());
    }
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType) type;
      return new TypeName(classType.getClassName());
    }
    return new TypeName(type.getCanonicalText());
  }
}
