package depends.matrix.transform;
/*
MIT License

Copyright (c) 2018-2019 Gang ZHANG

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import depends.matrix.core.DependencyMatrix;
import depends.matrix.core.DependencyPair;
import depends.matrix.core.DependencyValue;

import java.util.ArrayList;
import java.util.HashMap;

public class OrderedMatrixGenerator  {
	private DependencyMatrix matrix;
	public OrderedMatrixGenerator(DependencyMatrix matrix) {
		this.matrix = matrix;
	}
	public DependencyMatrix build() {
	    ArrayList<String> reMappedNodes= new ArrayList<>(matrix.getNodes());
	    //sort nodes by name
		reMappedNodes.sort((o1, o2) -> o1.compareTo(o2));
	    
	    DependencyMatrix ordered = new DependencyMatrix((int)(matrix.getDependencyPairs().size()/0.75+1), matrix.isOutputSelfDependencies());
		HashMap<String, Integer> nodesMap = new HashMap<>();
		for (int id=0;id<reMappedNodes.size();id++) {
			nodesMap.put(reMappedNodes.get(id), id);
			ordered.addNode(reMappedNodes.get(id), id);
		}

		//add dependencies
		for (DependencyPair dependencyPair:matrix.getDependencyPairs()) {
			Integer from = dependencyPair.getFrom();
			Integer to = dependencyPair.getTo();
			for (DependencyValue dep:dependencyPair.getDependencies()) {
				ordered.addDependency(dep.getType(), translateToNewId( nodesMap, from), translateToNewId( nodesMap, to), dep.getWeight(),dep.getDetails());
			}
		}
		return ordered;
	}
	private Integer translateToNewId( HashMap<String, Integer> nodesMap, Integer id) {
		return nodesMap.get(matrix.getNodeName(id));
	}

}
