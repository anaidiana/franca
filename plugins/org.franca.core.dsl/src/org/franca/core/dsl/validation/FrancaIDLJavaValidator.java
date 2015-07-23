/*******************************************************************************
 * Copyright (c) 2014 itemis AG (http://www.itemis.de).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.franca.core.dsl.validation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.ValidationMessageAcceptor;
import org.franca.core.FrancaModelExtensions;
import org.franca.core.dsl.validation.internal.ContractValidator;
import org.franca.core.dsl.validation.internal.CyclicDependenciesValidator;
import org.franca.core.dsl.validation.internal.OverloadingValidator;
import org.franca.core.dsl.validation.internal.TypesValidator;
import org.franca.core.dsl.validation.internal.ValidationHelpers;
import org.franca.core.dsl.validation.internal.ValidationMessageReporter;
import org.franca.core.dsl.validation.internal.ValidatorRegistry;
import org.franca.core.framework.FrancaHelpers;
import org.franca.core.franca.FAnnotation;
import org.franca.core.franca.FAnnotationType;
import org.franca.core.franca.FArgument;
import org.franca.core.franca.FAssignment;
import org.franca.core.franca.FAttribute;
import org.franca.core.franca.FBroadcast;
import org.franca.core.franca.FConstantDef;
import org.franca.core.franca.FContract;
import org.franca.core.franca.FDeclaration;
import org.franca.core.franca.FEnumerationType;
import org.franca.core.franca.FEnumerator;
import org.franca.core.franca.FEvaluableElement;
import org.franca.core.franca.FField;
import org.franca.core.franca.FGuard;
import org.franca.core.franca.FIntegerInterval;
import org.franca.core.franca.FInterface;
import org.franca.core.franca.FMethod;
import org.franca.core.franca.FModel;
import org.franca.core.franca.FQualifiedElementRef;
import org.franca.core.franca.FStructType;
import org.franca.core.franca.FTrigger;
import org.franca.core.franca.FType;
import org.franca.core.franca.FTypeCollection;
import org.franca.core.franca.FTypeRef;
import org.franca.core.franca.FTypedElement;
import org.franca.core.franca.FUnionType;
import org.franca.core.franca.FrancaPackage;

import com.google.inject.Inject;

/**
 *  This Java class is an intermediate class in the hierarchy of 
 *  validators for Franca IDL. It is still here for historical reasons.
 *  
 *  Please implement new validation methods in FrancaIDLValidator.xtend.
 */
public class FrancaIDLJavaValidator extends AbstractFrancaIDLJavaValidator
		implements ValidationMessageReporter {
	
	@Inject
	protected CyclicDependenciesValidator cyclicDependenciesValidator; 
	
	@Inject
	protected IQualifiedNameProvider qnProvider;

	/**
	 * Call external validators (those have been installed via an
	 * Eclipse extension point).
	 */
	@Check
	public void checkExtensionValidators(FModel model) {
		CheckMode mode = getCheckMode();
		for (IFrancaExternalValidator validator : ValidatorRegistry.getValidatorMap().get(mode)) {
			validator.validateModel(model, getMessageAcceptor());
		}
	}

	
	@Check
	public void checkTypeNamesUnique(FTypeCollection collection) {
		ValidationHelpers.checkDuplicates(this, collection.getTypes(),
				FrancaPackage.Literals.FMODEL_ELEMENT__NAME, "type name");
	}

	@Check
	public void checkTypeNamesUnique(FInterface iface) {
		ValidationHelpers.checkDuplicates(this, iface.getTypes(),
				FrancaPackage.Literals.FMODEL_ELEMENT__NAME, "type name");
	}

	@Check
	public void checkConstantNamesUnique(FTypeCollection collection) {
		ValidationHelpers.checkDuplicates(this, collection.getConstants(),
				FrancaPackage.Literals.FMODEL_ELEMENT__NAME, "constant name");
	}

	@Check
	public void checkConstantNamesUnique(FInterface iface) {
		ValidationHelpers.checkDuplicates(this, iface.getConstants(),
				FrancaPackage.Literals.FMODEL_ELEMENT__NAME, "constant name");
	}

	@Check
	public void checkStructHasElements(FStructType type) {
		if (type.getBase()==null && type.getElements().isEmpty() &&
				! type.isPolymorphic()) {
			error("Non-polymorphic structs must have own or inherited elements",
					type,
					FrancaPackage.Literals.FMODEL_ELEMENT__NAME, -1);
		}
	}
	
	@Check
	public void checkUnionHasElements(FUnionType type) {
		if (type.getBase()==null && type.getElements().isEmpty()) {
			error("Union must have own or inherited elements",
					type,
					FrancaPackage.Literals.FMODEL_ELEMENT__NAME, -1);
		}
	}
	
	@Check
	public void checkEnumerationHasEnumerators(FEnumerationType type) {
		if (type.getEnumerators().isEmpty()) {
			error("Enumeration must not be empty",
					type,
					FrancaPackage.Literals.FMODEL_ELEMENT__NAME, -1);
		}
	}


	@Check
	public void checkMethodFlags(FMethod method) {
		if (method.isFireAndForget()) {
			if (!method.getOutArgs().isEmpty()) {
				error("Fire-and-forget methods cannot have out arguments",
						method,
						FrancaPackage.Literals.FMETHOD__FIRE_AND_FORGET, -1);
			}
			if (method.getErrorEnum() != null || method.getErrors() != null) {
				error("Fire-and-forget methods cannot have error return codes",
						method,
						FrancaPackage.Literals.FMETHOD__FIRE_AND_FORGET, -1);
			}
		}
	}

	@Check
	public void checkMethodArgsUnique(FMethod method) {
		ValidationHelpers.checkDuplicates(this, method.getInArgs(),
				FrancaPackage.Literals.FMODEL_ELEMENT__NAME, "argument name");
		ValidationHelpers.checkDuplicates(this, method.getOutArgs(),
				FrancaPackage.Literals.FMODEL_ELEMENT__NAME, "argument name");
		
		// check if in- and out-arguments are pairwise different
		Map<String, FArgument> inArgs = new HashMap<String, FArgument>();
		for(FArgument a : method.getInArgs()) {
			inArgs.put(a.getName(), a);
		}
		for(FArgument a : method.getOutArgs()) {
			String key = a.getName();
			if (inArgs.containsKey(key)) {
				String msg = "Duplicate argument name '" + key + "' used for in and out"; 
				error(msg, inArgs.get(key), FrancaPackage.Literals.FMODEL_ELEMENT__NAME, -1);
				error(msg, a, FrancaPackage.Literals.FMODEL_ELEMENT__NAME, -1);
			}
		}
	}

	@Check
	public void checkBroadcastArgsUnique(FBroadcast bc) {
		ValidationHelpers.checkDuplicates(this, bc.getOutArgs(),
				FrancaPackage.Literals.FMODEL_ELEMENT__NAME, "argument name");
	}

	@Check
	public void checkConsistentInheritance(FInterface api) {
		ValidationHelpers.checkDuplicates(this,
				FrancaHelpers.getAllAttributes(api),
				FrancaPackage.Literals.FMODEL_ELEMENT__NAME,
				"attribute");
		
		// methods and broadcasts will be checked by the OverloadingValidator

		ValidationHelpers.checkDuplicates(this, FrancaHelpers.getAllTypes(api),
				FrancaPackage.Literals.FMODEL_ELEMENT__NAME, "type");

		ValidationHelpers.checkDuplicates(this, FrancaHelpers.getAllConstants(api),
				FrancaPackage.Literals.FMODEL_ELEMENT__NAME, "constant");

		if (api.getContract() != null && FrancaHelpers.hasBaseContract(api)) {
			error("Interface cannot overwrite base contract",
					api.getContract(),
					FrancaPackage.Literals.FINTERFACE__CONTRACT, -1);
		}
	}
	
	@Check
	public void checkOverloadedMethods(FInterface api) {
		OverloadingValidator.checkOverloadedMethods(this, api);
	}

	@Check
	public void checkOverloadedBroadcasts(FInterface api) {
		OverloadingValidator.checkOverloadedBroadcasts(this, api);
	}
	

	@Check
	public void checkCyclicDependencies(FModel m) {
		cyclicDependenciesValidator.check(this, m);
	}

	
	/**
	 * Check order of elements in an interface.
	 * 
	 * The contract should be at the end of the interface definition.
	 * In Franca 0.9.0 and older, there was a fixed order of elements in the
	 * interface. With 0.9.1 and later, the order can be changed, but the contract
	 * has to be at the end of the interface.
	 * 
	 * For backward compatibility reasons, we still allow constants and type definitions
	 * after the contract, but will mark these as deprecated.
	 * 
	 *  @see https://code.google.com/a/eclipselabs.org/p/franca/issues/detail?id=104#c1
	 * @param api the Franca interface
	 */
	@Check
	public void checkElementOrder(FInterface api) {
		if (api.getContract()!=null) {
			INode contractNode = NodeModelUtils.getNode(api.getContract());
			if (contractNode==null)
				return;
			
			int contractOffset = contractNode.getOffset();
			
			// check against all constant and type definitions
			String msg = "Deprecated order of interface elements (contract should be at the end)";
			for(FConstantDef i : api.getConstants()) {
				INode node = NodeModelUtils.getNode(i);
				if (node!=null) {
					int offset = node.getOffset();
					if (offset > contractOffset) {
						warning(msg, api, FrancaPackage.Literals.FTYPE_COLLECTION__CONSTANTS, api.getConstants().indexOf(i));
					}
				}
			}
			for(FType i : api.getTypes()) {
				INode node = NodeModelUtils.getNode(i);
				if (node!=null) {
					int offset = node.getOffset();
					if (offset > contractOffset) {
						warning(msg, api, FrancaPackage.Literals.FTYPE_COLLECTION__TYPES, api.getTypes().indexOf(i));
					}
				}
			}
		}
	}
	

	// *****************************************************************************

	// type-related checks
	
	@Check
	public void checkConstantDef (FConstantDef constantDef) {
		TypesValidator.checkConstantType(this, constantDef);
	}

	@Check
	public void checkDeclaration (FDeclaration declaration) {
		TypesValidator.checkConstantType(this, declaration);
	}

	@Check
	public void checkEnumValue (FEnumerator enumerator) {
		if (enumerator.getValue() != null)
			TypesValidator.checkEnumValueType(this, enumerator);
	}

	@Check
	public void checkIntegerInterval (FTypeRef intervalType) {
		if (intervalType.getInterval()!=null) {
			FIntegerInterval interval = intervalType.getInterval();
			if (interval.getLowerBound()!=null && interval.getUpperBound()!=null) {
				if (interval.getLowerBound().compareTo(interval.getUpperBound()) > 0) {
					error("Invalid interval specification", intervalType,
							FrancaPackage.Literals.FTYPE_REF__INTERVAL, -1);
				}
			}
		}
	}

	
	// *****************************************************************************

	@Check
	public void checkContract(FContract contract) {
		ContractValidator.checkContract(this, contract);
	}

	@Check
	public void checkTrigger(FTrigger trigger) {
		ContractValidator.checkTrigger(this, trigger);
	}

	@Check
	public void checkAssignment(FAssignment assignment) {
		ContractValidator.checkAssignment(this, assignment);
	}

	@Check
	public void checkGuard(FGuard guard) {
		ContractValidator.checkGuard(this, guard);
	}

	
	// visibility of derived types

	@Check
	public void checkTypeVisible(FTypeRef typeref) {
		if (typeref.getDerived() != null) {
			// this is a derived type, check if referenced type can be accessed
			FType referencedType = typeref.getDerived();
			checkDefinitionVisible(typeref, referencedType,
					"Type " + referencedType.getName(),
					FrancaPackage.Literals.FTYPE_REF__DERIVED
			);
		}
	}
	
	@Check
	public void checkTypedElementRefVisible(FQualifiedElementRef qe) {
		FEvaluableElement referenced = qe.getElement();
		if (referenced!=null && referenced instanceof FTypedElement) {
			checkDefinitionVisible(qe, referenced,
					getTypeLabel(referenced) + " " + referenced.getName(),
					FrancaPackage.Literals.FQUALIFIED_ELEMENT_REF__ELEMENT
			);
		}
	}
	
	private String getTypeLabel(FEvaluableElement elem) {
		if (elem instanceof FArgument) {
			return "Argument";
		} else if (elem instanceof FAttribute) {
			return "Attribute";
		} else if (elem instanceof FConstantDef) {
			return "Constant";
		} else if (elem instanceof FDeclaration) {
			return "State variable";
		} else if (elem instanceof FField) {
			return "Element of struct or union";
		} else if (elem instanceof FEnumerator) {
			return "Enumerator";
		} else {
			// sensible default
			return "Model element";
		}
	}
	
	private void checkDefinitionVisible(
			EObject referrer,
			EObject referenced,
			String what,
			EReference referencingFeature
	) {
		FInterface target = FrancaModelExtensions.getInterface(referenced);
		if (target == null) {
			// referenced element is defined by a type collection, can be accessed freely
		} else {
			// referenced element is defined by an FInterface, check if reference is allowed
			// by local access (same FInterface) or from a base interface via inheritance
			FInterface referrerInterface = FrancaModelExtensions.getInterface(referrer);
			boolean showError = false;
			if (referrerInterface==null) {
				// referrer is a type collection, it cannot reference a type from an interface
				showError = true;
			} else {
				Set<FInterface> baseInterfaces =
						FrancaModelExtensions.getInterfaceInheritationSet(referrerInterface);
				if (! baseInterfaces.contains(target)) {
					showError = true;
				}
			}
			if (showError) {
				error(what + " can only be referenced inside interface "
					+ target.getName() + " or derived interfaces",
					referrer,
					referencingFeature, -1
				);
			}
		}
	}

	// *****************************************************************************

	@Check
	public void checkAnnotationType (FAnnotation annotation) {
		FAnnotationType type = annotation.getType();
		if (type==null) {
			error("Invalid annotation type", annotation,
					FrancaPackage.Literals.FANNOTATION__RAW_TEXT, -1);
		}
	}

	// *****************************************************************************

	// ValidationMessageReporter interface
	public void reportError(String message, EObject object,
			EStructuralFeature feature) {
		error(message, object, feature,
				ValidationMessageAcceptor.INSIGNIFICANT_INDEX);
	}

	public void reportError(String message, EObject object,
			EStructuralFeature feature, int index) {
		error(message, object, feature, index);
	}

	public void reportWarning(String message, EObject object,
			EStructuralFeature feature) {
		warning(message, object, feature,
				ValidationMessageAcceptor.INSIGNIFICANT_INDEX);
	}
}
