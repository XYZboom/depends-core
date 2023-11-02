package depends.addons;

import org.junit.Test;

import java.util.ArrayList;

public class DV8MappingFileBuilderTest {

	@Test
	public void test() {
		DV8MappingFileBuilder b = new DV8MappingFileBuilder(new ArrayList<>());
		b.create("/tmp/depends-dv8-mapping.json");
	}

}
