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
package org.assertj.assertions.generator.description.converter;

import static org.assertj.assertions.generator.util.PsiClassUtil.getterMethodsOf;
import static org.assertj.assertions.generator.util.PsiClassUtil.propertyNameOf;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.assertj.assertions.generator.description.ClassDescription;
import org.assertj.assertions.generator.description.GetterDescription;
import org.assertj.assertions.generator.description.TypeDescription;
import org.assertj.assertions.generator.description.TypeName;
import org.assertj.assertions.generator.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

public class PsiClassToClassDescriptionConverter implements ClassDescriptionConverter<PsiClass> {
  private static final Logger logger = Logger.getInstance(PsiClassToClassDescriptionConverter.class);

  @NotNull
  private final Project project;

  private final PsiClassUtil psiClassUtil;

  public PsiClassToClassDescriptionConverter(@NotNull Project project) {
    this.project = project;
    this.psiClassUtil = new PsiClassUtil(project);
  }

  public ClassDescription convertToClassDescription(PsiClass clazz) {
    // PsiPackage pkg = JavaPsiFacade.getInstance(project).findPackage(javaFile.getPackageName());
    PsiClassUtil psiClassUtil = new PsiClassUtil(project);
    ClassDescription classDescription = new ClassDescription(psiClassUtil.getTypeName(clazz));
    classDescription.addGetterDescriptions(getterDescriptionsOf(clazz));
    classDescription.addTypeToImport(getNeededImportsFor(clazz));
    return classDescription;
  }

  @VisibleForTesting
  protected Set<GetterDescription> getterDescriptionsOf(PsiClass clazz) {
    Set<GetterDescription> getterDescriptions = new TreeSet<GetterDescription>();

    List<PsiMethod> getters = getterMethodsOf(clazz);
    logger.info("Getters ; " + getters);
    for (PsiMethod getter : getters) {
      final TypeDescription typeDescription = getTypeDescription(clazz, getter);
      logger.info("Getter ; " + getter + " typeDescription : " + typeDescription);
      getterDescriptions.add(new GetterDescription(propertyNameOf(getter), typeDescription));
    }
    return getterDescriptions;
  }

  @VisibleForTesting
  protected TypeDescription getTypeDescription(PsiClass clazz, PsiMethod getter) {
    final PsiType propertyType = getter.getReturnType();
    final TypeDescription typeDescription = new TypeDescription(psiClassUtil.getTypeName(propertyType));
    if (psiClassUtil.isArray(propertyType)) {
      typeDescription.setElementTypeName(new TypeName(propertyType.getPresentableText()));
      typeDescription.setArray(true);
    } else if (psiClassUtil.isIterable(propertyType)) {
      typeDescription.setIterable(true);

      PsiClassType classType = (PsiClassType) propertyType;
      typeDescription.setElementTypeName(new TypeName(classType.getInternalCanonicalText()));
      // SetArray ?
    }
    return typeDescription;
  }

  /**
   * Get the underlying class for a type, or null if the type is a variable type.
   * 
   * @param type the type
   * @return the underlying class
   */
  public static Class<?> getClass(final Type type) {
    if (type instanceof Class) {
      return (Class<?>) type;
    } else if (type instanceof ParameterizedType) {
      return getClass(((ParameterizedType) type).getRawType());
    } else if (type instanceof GenericArrayType) {
      final Type componentType = ((GenericArrayType) type).getGenericComponentType();
      final Class<?> componentClass = getClass(componentType);
      if (componentClass != null) {
        return Array.newInstance(componentClass, 0).getClass();
      } else {
        return null;
      }
    } else if (type instanceof WildcardType) {
      final WildcardType wildcard = (WildcardType) type;
      return wildcard.getUpperBounds() != null ? getClass(wildcard.getUpperBounds()[0])
          : wildcard.getLowerBounds() != null ? getClass(wildcard.getLowerBounds()[0]) : null;
    } else {
      return null;
    }
  }

  @VisibleForTesting
  protected Set<TypeName> getNeededImportsFor(PsiClass clazz) {
    // collect property types
    Set<PsiClass> classesToImport = new HashSet<PsiClass>();
    for (PsiMethod getter : getterMethodsOf(clazz)) {
      getter.getReturnTypeElement();
      PsiType propertyType = getter.getReturnType();
      // if (psiClassUtil.isArray(propertyType)) {
      // // we only need the component type, that is T in T[] array
      // classesToImport.add(propertyType.getComponentType());
      // } else if (psiClassUtil.isIterable(propertyType)) {
      // // we need the Iterable parameter type, that is T in Iterable<T>
      // // we don't need to import the Iterable since it does not appear directly in generated code, ex :
      // // assertThat(actual.getTeamMates()).contains(teamMates); // teamMates -> List
      // ParameterizedType parameterizedType = (ParameterizedType) getter.getGenericReturnType();
      // classesToImport.add(getClass(parameterizedType.getActualTypeArguments()[0]));
      // } else if (getter.getGenericReturnType() instanceof ParameterizedType) {
      // // return type is generic type, add it and all its parameters type.
      // ParameterizedType parameterizedType = (ParameterizedType) getter.getGenericReturnType();
      // classesToImport.addAll(getClassesRelatedTo(parameterizedType));
      // } else {
      // // return type is not generic type, simply add it.
      // classesToImport.add(propertyType);
      // }
    }
    // convert to TypeName, excluding primitive or types in java.lang that don't need to be imported.
    Set<TypeName> typeToImports = new TreeSet<TypeName>();
    for (PsiClass propertyType : classesToImport) {
      // Package can be null in case of array of primitive.
      typeToImports.add(psiClassUtil.getTypeName(propertyType));
      // if (!propertyType.isPrimitive()
      // && (propertyType.getPackage() != null && !JAVA_LANG_PACKAGE.equals(propertyType.getPackage().getName()))) {
      // typeToImports.add(new TypeName(propertyType));
      // }
    }
    return typeToImports;
  }

}
