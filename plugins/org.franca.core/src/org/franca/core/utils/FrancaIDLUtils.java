/*******************************************************************************
 * Copyright (c) 2012 Harman International (http://www.harman.com).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.franca.core.utils;

import java.util.Iterator;
import java.util.List;

import org.franca.core.franca.FArrayType;
import org.franca.core.franca.FEnumerationType;
import org.franca.core.franca.FField;
import org.franca.core.franca.FMapType;
import org.franca.core.franca.FModel;
import org.franca.core.franca.FStructType;
import org.franca.core.franca.FType;
import org.franca.core.franca.FTypeCollection;
import org.franca.core.franca.FTypeDef;
import org.franca.core.franca.FTypeRef;
import org.franca.core.franca.FUnionType;
import org.franca.core.utils.digraph.Digraph;
import org.franca.core.utils.digraph.Digraph.HasCyclesException;

/**
 * Utilities for Franca.
 * 
 * @author FPicioroaga
 * 
 */
public class FrancaIDLUtils {

	/**
	 * This will sort all the types in the model in the order they should appear in a C++ header without forward
	 * declarations, i.e. first the base types and then the derived ones.
	 * 
	 * @param idlModel
	 *            the model delivering the types
	 * @return
	 */
	public static List<FType> getDataTypesSorted(FModel idlModel) {
		Digraph<FType> typesDigraph = new Digraph<FType>();

		/**
		 * go to all types and add the corresponding dependencies in the digraph
		 */
		for (Iterator<FTypeCollection> tc = idlModel.getTypeCollections().iterator(); tc.hasNext();) {
			for (Iterator<FType> it = tc.next().getTypes().iterator(); it.hasNext();) {
				FType ftype = it.next();
	
				if (ftype instanceof FArrayType) {
					FArrayType specificType = (FArrayType) ftype;
					FType childType = specificType.getElementType().getDerived();
	
					if (childType != null) {
						typesDigraph.addEdge(childType, ftype);
					}
				}
				if (ftype instanceof FEnumerationType) {
					FEnumerationType specificType = (FEnumerationType) ftype;
					FType childType = specificType.getBase();
	
					if (childType != null) {
						typesDigraph.addEdge(childType, ftype);
					}
				}
				if (ftype instanceof FStructType) {
					FStructType specificType = (FStructType) ftype;
					FType childType = specificType.getBase();
	
					if (childType != null) {
						typesDigraph.addEdge(childType, ftype);
					}
					for (Iterator<FField> filedsIt = specificType.getElements().iterator(); filedsIt.hasNext();) {
						childType = filedsIt.next().getType().getDerived();
	
						if (childType != null) {
							typesDigraph.addEdge(childType, ftype);
						}
					}
				}
				if (ftype instanceof FUnionType) {
					FUnionType specificType = (FUnionType) ftype;
					FType childType = specificType.getBase();
	
					if (childType != null) {
						typesDigraph.addEdge(childType, ftype);
					}
					for (Iterator<FField> filedsIt = specificType.getElements().iterator(); filedsIt.hasNext();) {
						childType = filedsIt.next().getType().getDerived();
	
						if (childType != null) {
							typesDigraph.addEdge(childType, ftype);
						}
					}
				}
				if (ftype instanceof FMapType) {
					FMapType specificType = (FMapType) ftype;
					FType childType = specificType.getKeyType().getDerived();
	
					if (childType != null) {
						typesDigraph.addEdge(childType, ftype);
					}
					childType = specificType.getValueType().getDerived();
					if (childType != null) {
						typesDigraph.addEdge(childType, ftype);
					}
				}
				if (ftype instanceof FTypeRef) {
					FTypeRef specificType = (FTypeRef) ftype;
					FType childType = specificType.getDerived();
	
					if (childType != null) {
						typesDigraph.addEdge(childType, ftype);
					}
	
				}
				if (ftype instanceof FTypeDef) {
					FTypeDef specificType = (FTypeDef) ftype;
					FType childType = specificType.getActualType().getDerived();
	
					if (childType != null) {
						typesDigraph.addEdge(childType, ftype);
					}
				}
			}
		}
	
		// after the digraph is prepared return the topological sorted list of nodes
		try {
			return typesDigraph.topoSort();
		} catch (HasCyclesException e) {
			System.out.println("Cycles were detected in: " + typesDigraph);
		}
		return null;
	}
}
