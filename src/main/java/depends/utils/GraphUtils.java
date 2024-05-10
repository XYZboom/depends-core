package depends.utils;

import com.google.common.graph.Graph;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GraphUtils {
	/**
	 * Perform topology traversal on the given graph<br>
	 * 对所给的图进行拓扑遍历
	 *
	 * @param otherNodesFunction Traverse function for nodes that have not been traversed by {@code function}
	 */
	public static <T> void topologyTraverse(
			Graph<T> graph,
			Consumer<T> function,
			Consumer<T> otherNodesFunction
	) {
		topologyTraverse(graph, function, otherNodesFunction, null);
	}

	/**
	 * Perform topology traversal on the given graph<br>
	 * 对所给的图进行拓扑遍历
	 *
	 * @param otherNodesFunction Traverse function for nodes that have not been traversed by {@code function}
	 * @param nodeComparator Comparator of node 节点比较器
	 */
	public static <T> void topologyTraverse(
			Graph<T> graph,
			Consumer<T> function,
			Consumer<T> otherNodesFunction,
			Comparator<T> nodeComparator) {
		if (nodeComparator == null) {
			nodeComparator = Comparator.comparing(Objects::hashCode);
		}
		Queue<T> queue = new PriorityQueue<>(32, nodeComparator);
		graph.nodes().stream()
				.filter(task -> graph.inDegree(task) == 0)
				.forEach(queue::add);
		Map<T, Integer> taskIntegerMap = graph.nodes().stream()
				.filter(task -> graph.inDegree(task) != 0)
				.collect(Collectors.toMap(node -> node, graph::inDegree));
		while (!queue.isEmpty()) {
			T current = queue.poll();
			function.accept(current);
			graph.successors(current).forEach(node -> {
				Integer i = taskIntegerMap.get(node);
				if (--i == 0) {
					taskIntegerMap.remove(node);
					queue.offer(node);
				} else {
					taskIntegerMap.put(node, i);
				}
			});
		}
		for (T node : taskIntegerMap.keySet()) {
			otherNodesFunction.accept(node);
		}
	}
}
