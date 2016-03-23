package org.franca.connectors.protobuf.tests

import com.google.inject.Inject
import java.util.List
import org.eclipse.emf.compare.Diff
import org.eclipse.emf.compare.EMFCompare
import org.eclipse.emf.compare.internal.spec.ResourceAttachmentChangeSpec
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.junit4.InjectWith
import org.eclipselabs.xtext.utils.unittesting.XtextRunner2
import org.franca.connectors.protobuf.ProtobufConnector
import org.franca.connectors.protobuf.ProtobufModelContainer
import org.franca.core.dsl.FrancaIDLTestsInjectorProvider
import org.franca.core.dsl.FrancaPersistenceManager
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

import static org.junit.Assert.assertEquals

@RunWith(typeof(XtextRunner2))
@InjectWith(typeof(FrancaIDLTestsInjectorProvider))
class Protobuf2FrancaTests {

	val MODEL_DIR = "model/testcases/"
	val REF_DIR = "model/reference/"
	val GEN_DIR = "src-gen/testcases/"

	@Inject extension FrancaPersistenceManager

	@Test
	def test_Empty() {
		test("EmptyService")
		test("EmptyMessage")
	}
	
	@Test 
	def messageWithScalarValueTypeFields(){
		test("MessageWithScalarValueTypeFields")
	}

	@Test
	def test_ServiceWithOneRPC(){
		test("ServiceWithOneRPC")
	}
	
	@Test
	def test_MessageWithComplexTypeFields(){
		test("MessageWithComplexTypeFields")
	}
	
	@Test
	def test_MessageWithComplexType(){
		test("MessageWithComplexType")
	}
	
	@Test
	def test_MessageWithMessageField() {
		test("MessageWithMessageField")
	}

	@Test
	def test_OneOf() {
		test("MessageWithOneof")
	}
	
	@Test
	def test_Extend() {
		test("MessageWithExtend")
	}
	
	@Test
	@Ignore
	//FIXME 
	def test_Import() {
		test("MultiFiles")
	}

	@Test
	@Ignore
	def test_Option() {
		//test("Option")
		test("EnumWithOption")
	}
	
	@Test
	//FIXME Franca Serializer issues : the subtraction operator is separated from the number. 
	def enum_01(){
		test("Enum_01")
	}

	/**
	 * Utility method for executing one transformation and comparing the result with a reference model.
	 */
	def private test(String inputfile) {
		val PROTOBUF_EXT = ".proto"
		val FRANCA_IDL_EXT = ".fidl"

		// load the OMG IDL input model
		val conn = new ProtobufConnector
		val protobufidl = conn.loadModel(MODEL_DIR + inputfile + PROTOBUF_EXT) as ProtobufModelContainer

		// do the actual transformation to Franca IDL and save the result
		val fmodelGen = conn.toFranca(protobufidl)
		EcoreUtil.resolveAll(fmodelGen)
		fmodelGen.saveModel(GEN_DIR + inputfile + FRANCA_IDL_EXT)

		// load the reference Franca IDL model
		val fmodelRef = loadModel(REF_DIR + inputfile + FRANCA_IDL_EXT)
		EcoreUtil.resolveAll(fmodelRef.eResource)

		// use EMF Compare to compare both Franca IDL models (the generated and the reference model)
		val rset2 = fmodelRef.eResource.resourceSet
		val rset1 = fmodelGen.eResource.resourceSet

		val scope = EMFCompare.createDefaultScope(rset1, rset2)
		val comparison = EMFCompare.builder.build.compare(scope)
		val List<Diff> differences = comparison.differences
		var nDiffs = 0
		for (diff : differences) {
			if (! (diff instanceof ResourceAttachmentChangeSpec)) {
				System.out.println(diff.toString)
				nDiffs++
			}
		}

		// TODO: is there a way to show the difference in a side-by-side view if the test fails?
		// (EMF Compare should provide a nice view for this...)		
		// we expect that both Franca IDL models are identical 
		assertEquals(0, nDiffs)
	}
}