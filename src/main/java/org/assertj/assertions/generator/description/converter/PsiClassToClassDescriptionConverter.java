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

    // Hack for "no package"
//    if ("".equals(classDescription.getPackageName())) {
//
//    }
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
      typeDescription.setElementTypeName(new TypeName(classType.getDeepComponentType().getCanonicalText()));

      logger.info("I am a " + classType.getClassName() + " 1 :" + classType.getInternalCanonicalText());
      logger.info("I am a " + classType.getClassName() + " 1 :" + classType.getCanonicalText());
      // SetArray ?
    }
    return typeDescription;
  }

  @VisibleForTesting
  protected Set<TypeName> getNeededImportsFor(PsiClass clazz) {
    // collect property types
    Set<TypeName> typeToImports = new TreeSet<TypeName>();
    for (PsiMethod getter : getterMethodsOf(clazz)) {
      PsiType type = getter.getReturnType();
      if (type instanceof PsiClassType) {
        PsiClassType classType = (PsiClassType) type;
        typeToImports.add(new TypeName(classType.getClassName()));
      }
    }
    return typeToImports;
  }
}
