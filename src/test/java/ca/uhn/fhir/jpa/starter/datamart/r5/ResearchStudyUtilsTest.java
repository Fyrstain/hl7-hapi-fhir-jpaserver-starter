package ca.uhn.fhir.jpa.starter.datamart.r5;

import ca.uhn.fhir.jpa.starter.datamart.service.CryptoUtils;
import ca.uhn.fhir.jpa.starter.datamart.service.r5.utils.ResearchStudyUtils;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r5.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.opencds.cqf.fhir.api.Repository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResearchStudyUtilsTest {

	@Mock
	private Repository repository;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void getEvidenceVariableSuccess() {
		ResearchStudy study = new ResearchStudy();
		study.setUrl("Study/1");
		Extension rootExt = new Extension(ResearchStudyUtils.EXT_URL);
		Extension varExt = new Extension(ResearchStudyUtils.VAR_EXT_NAME);
		varExt.setValue(new Reference("EvidenceVariable/ev1"));
		rootExt.addExtension(varExt);
		study.addExtension(rootExt);

		EvidenceVariable ev = new EvidenceVariable();
		ev.setId("ev1");
		when(repository.read(eq(EvidenceVariable.class), any(IIdType.class))).thenReturn(ev);

		EvidenceVariable result = ResearchStudyUtils.getEvidenceVariable(study, repository);

		assertNotNull(result);
		assertEquals("ev1", result.getId());
		ArgumentCaptor<IIdType> captor = ArgumentCaptor.forClass(IIdType.class);
		verify(repository).read(eq(EvidenceVariable.class), captor.capture());
		assertEquals("ev1", captor.getValue().getIdPart());
	}

	@Test
	void getEvidenceVariableMissingExtension() {
		ResearchStudy study = new ResearchStudy();
		study.setUrl("Study/2");
		ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
			ResearchStudyUtils.getEvidenceVariable(study, repository)
		);
		assertTrue(ex.getMessage().contains("does not contain extension"));
	}

	@Test
	void getEvidenceVariableMissingVarExtension() {
		ResearchStudy study = new ResearchStudy();
		study.setUrl("Study/3");
		Extension rootExt = new Extension(ResearchStudyUtils.EXT_URL);
		study.addExtension(rootExt);

		ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
			ResearchStudyUtils.getEvidenceVariable(study, repository)
		);
		assertTrue(ex.getMessage().contains("does not contain sub-extension"));
	}

	@Test
	void getEvidenceVariableInvalidRefType() {
		ResearchStudy study = new ResearchStudy();
		study.setUrl("Study/4");
		Extension rootExt = new Extension(ResearchStudyUtils.EXT_URL);
		Extension varExt = new Extension(ResearchStudyUtils.VAR_EXT_NAME);
		varExt.setValue(new Reference("Observation/obs1"));
		rootExt.addExtension(varExt);
		study.addExtension(rootExt);

		ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
			ResearchStudyUtils.getEvidenceVariable(study, repository)
		);
		assertTrue(ex.getMessage().contains("does not have a valid eligibility reference to an EvidenceVariable"));
	}

	@Test
	void getEligibleGroupSuccess() {
		ResearchStudy study = new ResearchStudy();
		study.setUrl("Study/5");
		study.getRecruitment().setActualGroup(new Reference("Group/g1"));

		Group group = new Group();
		group.setId("g1");
		Group.GroupMemberComponent member = new Group.GroupMemberComponent();
		member.setEntity(new Reference("Patient/p1"));
		group.addMember(member);
		when(repository.read(eq(Group.class), any(IIdType.class))).thenReturn(group);

		Group result = ResearchStudyUtils.getEligibleGroup(study, repository);
		assertNotNull(result);
		assertEquals("g1", result.getId());
	}

	@Test
	void getEligibleGroupNoGroup() {
		ResearchStudy study = new ResearchStudy();
		study.setUrl("Study/6");
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
			ResearchStudyUtils.getEligibleGroup(study, repository)
		);
		assertTrue(ex.getMessage().contains("does not have an actualGroup"));
	}

	@Test
	void getEligibleGroupInvalidRefType() {
		ResearchStudy study = new ResearchStudy();
		study.setUrl("Study/7");
		study.getRecruitment().setActualGroup(new Reference("Patient/p1"));
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
			ResearchStudyUtils.getEligibleGroup(study, repository)
		);
		System.out.print(ex.getMessage());
		assertTrue(ex.getMessage().contains("has an invalid actualGroup reference"));
	}

	@Test
	void getEvidenceVariableNullValueReference() {
		ResearchStudy study = new ResearchStudy().setUrl("Study/NullRef");
		Extension rootExt = new Extension(ResearchStudyUtils.EXT_URL);
		Extension varExt = new Extension(ResearchStudyUtils.VAR_EXT_NAME);
		rootExt.addExtension(varExt);
		study.addExtension(rootExt);

		ResourceNotFoundException ex = assertThrows(
				ResourceNotFoundException.class,
				() -> ResearchStudyUtils.getEvidenceVariable(study, repository)
		);
		assertTrue(ex.getMessage().contains("does not have a valid eligibility reference"));
	}

	@Test
	void getEligibleGroupNullReferenceElement() {
		ResearchStudy study = new ResearchStudy().setUrl("Study/NullGroupRef");
		Reference badRef = new Reference();
		study.getRecruitment().setActualGroup(badRef);

		IllegalArgumentException ex = assertThrows(
				IllegalArgumentException.class,
				() -> ResearchStudyUtils.getEligibleGroup(study, repository)
		);
		System.out.print(ex.getMessage());
		assertTrue(ex.getMessage().contains("does not have an actualGroup defined in recruitment"));
	}

	@Test
	void getSubjectReferencesNullMemberReferenceElement() {
		Group group = new Group();
		Group.GroupMemberComponent member = new Group.GroupMemberComponent();

		Reference badRef = new Reference();
		member.setEntity(badRef);
		group.addMember(member);

		ResourceNotFoundException ex = assertThrows(
				ResourceNotFoundException.class,
				() -> ResearchStudyUtils.getSubjectReferences(group, repository)
		);
		assertTrue(ex.getMessage().contains("invalid member reference"));
	}

	@Test
	void getEligibleGroupEmptyMembers() {
		ResearchStudy study = new ResearchStudy();
		study.setUrl("Study/8");
		study.getRecruitment().setActualGroup(new Reference("Group/g2"));
		Group emptyGroup = new Group();
		emptyGroup.setId("g2");
		when(repository.read(eq(Group.class), any(IIdType.class))).thenReturn(emptyGroup);

		ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
			ResearchStudyUtils.getEligibleGroup(study, repository)
		);
		assertTrue(ex.getMessage().contains("contains no members"));
	}

	@Test
	void getSubjectReferencesSuccess() {
		Group group = new Group();
		group.setId("g3");
		Group.GroupMemberComponent member = new Group.GroupMemberComponent();
		member.setEntity(new Reference().setIdentifier(new Identifier().setValue("enc1")));
		group.addMember(member);

		Patient p = new Patient();
		p.setId("real1");
		Bundle bundle = new Bundle();
		bundle.addEntry().setResource(p);

		try (MockedStatic<CryptoUtils> crypto = Mockito.mockStatic(CryptoUtils.class)) {
			crypto.when(() -> CryptoUtils.decrypt("enc1")).thenReturn("real1");

			when(repository.search(
				eq(Bundle.class),
				eq(Patient.class),
				anyMap(),
				isNull()
			)).thenReturn(bundle);

			List<String> refs = ResearchStudyUtils.getSubjectReferences(group, repository);

			assertEquals(1, refs.size());
			assertEquals("Patient/real1", refs.get(0));
		}
	}


	@Test
	void getSubjectReferencesEmptyGroup() {
		Group group = new Group();
		group.setId("g4");
		List<String> refs = ResearchStudyUtils.getSubjectReferences(group, repository);
		assertTrue(refs.isEmpty());
	}

	@Test
	void getSubjectReferencesInvalidMember() {
		Group group = new Group();
		group.setId("g5");
		Group.GroupMemberComponent member = new Group.GroupMemberComponent();
		member.setEntity(new Reference("Observation/o1"));
		group.addMember(member);

		ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
			ResearchStudyUtils.getSubjectReferences(group, repository)
		);
		assertTrue(ex.getMessage().contains("invalid member reference"));
	}
}

