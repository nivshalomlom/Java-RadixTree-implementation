import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RadixTree implements Serializable {

	// A random UID to handle serialization
	private static final long serialVersionUID = 7062331873348796464L;
	
	// Static cache and constants to be shared with every tree
	private static HashMap<String, List<String>> autocompleteCache;
	private static final int WORDS_IN_LINE = 5;
	
	// A lock to handle multi-threading cache uses
	private ReentrantLock mutex = new ReentrantLock();
	
	// Variables to keep the tree's root and size
	private RNode treeRoot;
	private int size;
	
	// Initialize a empty RadixTree
	public RadixTree() {
		this.treeRoot = new RNode(false);
		this.size = 0;
		autocompleteCache = new HashMap<String, List<String>>();
	}

	// Adds a string to the tree
	public void add(String string) {
		// If the tree is empty just add the string
		if (this.getTreeRoot().getChildren().isEmpty())
			this.getTreeRoot().addChild(string, true);
		// Otherwise do the algorithm to make the nodes
		else {
			try {
				// Check if a auto-complete that is relevant to the string about to be added is cached
				// If yes add the new word to that cached auto-complete 
				this.mutex.lock();
				for (String key : autocompleteCache.keySet())
					if (!getCommonPrefix(key, string).isEmpty() && key.length() <= string.length()) {
						autocompleteCache.get(key).add(string);
					}
			}
			finally {
				this.mutex.unlock();
				addInternal(string, this.getTreeRoot());
			}
		}
		this.size++;
	}
	
	// The internal recursive method to add tree insertion
	private void addInternal(String string, RNode ptr) {
		for (String child : ptr.getChildren()) {
			// Checking if string is already in tree
			if (string.equals(child)) {
				ptr.getChildByString(child).setIsEndOfWord(true);
				return;
			}
			// Checking if parts of the string to be added is in the tree and navigating to them
			if (string.startsWith(child)) {
				addInternal(string.substring(child.length()), ptr.getChildByString(child));
				return;
			}
		}
		// At this point we are in the deepest point in the tree we can get to 
		// With the given string
		for (String child : ptr.getChildren()) {
			// We assume the tree is valid and look for a child with a common prefix
			// With our string
			if (child.charAt(0) == string.charAt(0)) {
				// We calculate that prefix and use it to create a new node
				String commonPrefix = getCommonPrefix(string, child);
				// Here we split it to 2 nodes, 1 for the suffix of the child
				// And 1 for the string we want to add
				String newKey = child.substring(commonPrefix.length());
				RNode backup = ptr.getChildByString(child);
				ptr.removeChild(child);
				ptr.addChild(commonPrefix, false);
				// Adding the 2 new nodes after the split
				ptr = ptr.getChildByString(commonPrefix);
				ptr.addChild(newKey, backup);
				ptr.addChild(string.substring(commonPrefix.length()), true);
				return;
			}
		}
		// In case no child has a common prefix we just add it to the tree
		ptr.addChild(string, true);
	}
	
	// A method to remove a child from the tree
	public void remove(String string) {
		removeInternal(string, this.getTreeRoot());
		if (autocompleteCache.containsKey(string))
			autocompleteCache.remove(string);
		this.size--;
	}
	
	// The internal recursive method to add tree deletion
	private void removeInternal(String string, RNode ptr) {
		for (String child : ptr.getChildren()) {
			// Checking if string is already in tree
			if (string.equals(child)) {
				ptr.getChildByString(child).setIsEndOfWord(false);
				if (ptr.getChildByString(child).getChildren().isEmpty())
					ptr.getChildrenMap().remove(child);
				return;
			}
			// Checking if parts of the string to be added is in the tree and navigating to them
			if (string.startsWith(child)) {
				removeInternal(string.substring(child.length()), ptr.getChildByString(child));
				if (ptr.getChildByString(child).getChildren().isEmpty() && !ptr.getChildByString(child).isEndOfWord)
					ptr.getChildrenMap().remove(child);
				return;
			}
		}
	}
	
	// Reads strings from given file
	// The file should be formated with words with a delimiter between them e.g:
	// Hello,You
	// Are,Here,Door
	// If no delimiter is given the code assumes its "|" meaning it will assume the formatting is:
	// Hello|No|Yes
	// Maybe|Hey|Over here
	public void readStringsFromFile(String filepath) throws IOException {
		File stringsFile = new File(filepath);
		BufferedReader input = new BufferedReader(new FileReader(stringsFile));
		String buffer = "";
		while ((buffer = input.readLine()) != null) 
			for (String s : buffer.split("\\|"))
				this.add(s);
		input.close();
	}
	
	public void readStringsFromFile(String filepath, String lineDelimiter) throws IOException {
		File stringsFile = new File(filepath);
		BufferedReader input = new BufferedReader(new FileReader(stringsFile));
		String buffer = "";
		while ((buffer = input.readLine()) != null) 
			for (String s : buffer.split(lineDelimiter)) 
				this.add(s);
		input.close();
	}
	
	// Writes all words in the tree to a given file using a given delimiter
	public void writeStringsToFile(String filename, String lineDelimiter) throws IOException {
		BufferedWriter output = new BufferedWriter(new FileWriter(filename));
		Iterator<String> iterator = this.getAllStrings().iterator();
		int i = 0;
		StringBuilder buffer = new StringBuilder("");
		// Make a lines that has up to WORDS_IN_LINE(a constant) words in line
		// And write them to the file
		while (iterator.hasNext()) {
			buffer.append(iterator.next() + lineDelimiter);
			// When we reached the max words in line we write to the file and clean our buffer
			if (i++ == WORDS_IN_LINE) {
				output.write(buffer.substring(0, buffer.length() - 2) + "\n");
				buffer = new StringBuilder("");
				i = 0;
			}
		}
		if (!buffer.toString().isEmpty())
			output.write(buffer.substring(0, buffer.length() - 2) + "\n");
		output.close();
	}
	
	// Writes all words in the tree to a given file using the default delimiter
	public void writeStringsToFile(String filename) throws IOException {
		BufferedWriter output = new BufferedWriter(new FileWriter(filename));
		Iterator<String> iterator = this.getAllStrings().iterator();
		int i = 0;
		StringBuilder buffer = new StringBuilder("");
		// Make a lines that has up to WORDS_IN_LINE(a constant) words in line
		// And write them to the file
		while (iterator.hasNext()) {
			buffer.append(iterator.next() + "|");
			// When we reached the max words in line we write to the file and clean our buffer
			if (i++ == WORDS_IN_LINE) {
				output.write(buffer.substring(0, buffer.length() - 2) + "\n");
				buffer = new StringBuilder("");
				i = 0;
			}
		}
		if (!buffer.toString().isEmpty())
			output.write(buffer.substring(0, buffer.length() - 2) + "\n");
		output.close();
	}
	
	// Returns if the string is contained in the tree
	public boolean contains(String string) {
		return this.contains(string, this.getTreeRoot());
	}
	
	private boolean contains(String string, RNode node) {
		if (string.isEmpty())
			return node.isEndOfWord;
		for (String child : node.getChildren()) 
			if (string.startsWith(child)) {
				return contains(string.substring(child.length()), node.getChildByString(child));
			}
		return false;
	}
	
	// Returns a collection of every string in the tree
	public Collection<String> getAllStrings() {
		return Arrays.asList(getAllStrings(this.getTreeRoot(), "").split("\\|"));
	}
	
	private String getAllStrings(RNode node, String buffer) {
		String ret_val = node.isEndOfWord ? buffer + "|" : "";
		for (String child : node.getChildren()) 
			ret_val += getAllStrings(node.getChildByString(child), buffer + child);
		return ret_val;
	}
	
	// Returns the RNode at the root of the tree
	public RNode getTreeRoot() {
		return this.treeRoot;
	}
	
	// Returns how many words are stored in the tree
	public int stringsAmount() {
		return this.size;
	}
	
	// Returns the amount of nodes in the tree
	public int nodeAmount() {
		return nodeAmount(this.getTreeRoot());
	}
	
	private int nodeAmount(RNode node) {
		int ret_val = 1;
		for (String child : node.getChildren()) 
			ret_val += nodeAmount(node.getChildByString(child));
		return ret_val;
	}
	
	// A internal method to find a common prefix between two strings
	private String getCommonPrefix(String str1, String str2) {
		if (str1.isEmpty() || str2.isEmpty())
			return "";
		if (str1.charAt(0) == str2.charAt(0))
			return str1.charAt(0) + getCommonPrefix(str1.substring(1), str2.substring(1));
		return "";
	}
	
	// Deletes every string in the tree
	public void clear() {
		this.treeRoot = new RNode(false);
	}
	
	// Returns a given amount of strings in the tree that string is a prefix of
	public Collection<String> autocomplete(String string, int resultAmount) {
		if (autocompleteCache.containsKey(string)) 
			return trimList(autocompleteCache.get(string), 0, resultAmount);
		List<String> ret_val = new LinkedList<String>();
		RNode ptr = this.getTreeRoot();
		String backupString = string;
		// Here we navigate as low as we can in the tree using a copy of the given string
		// And a pointer to the tree
		outer_loop: while (true) {
			for (String child : ptr.getChildren()) {
				String prefix = this.getCommonPrefix(backupString, child);
				if (!prefix.isEmpty()) {
					ptr = ptr.getChildByString(child);
					if (ptr.isEndOfWord())
						ret_val.add(child);
					else {
						backupString = backupString.substring(child.length());
						if (backupString.isEmpty())
							break outer_loop;
						continue outer_loop;
					}
				}
			}
			break;
		}
		// If the copy of the given string is'nt empty after the navigation it is not
		// Part of a word so here we check that
		if (ptr.equals(this.getTreeRoot()))
			ret_val = autocomplete(ptr, "").stream().filter(i -> i.startsWith(string)).collect(Collectors.toList());
		else if (!ptr.isLeaf()) ret_val = autocomplete(ptr, string);
		return trimList(ret_val, 0, resultAmount);
	}

	// The internal method the makes auto-complete work
	private List<String> autocomplete(RNode ptr, String string) {
		List<String> ret_val = new LinkedList<String>();
		for (String child : ptr.getChildren()) {
			if (ptr.getChildByString(child).isEndOfWord()) 
				ret_val.add(string + "" + child);
			if (autocompleteCache.containsKey(string + "" + child))
				ret_val = Stream.concat(autocompleteCache.get(string + "" + child).stream(), ret_val.stream()).collect(Collectors.toList());
			else ret_val = Stream.concat(autocomplete(ptr.getChildByString(child), string + "" + child).stream(), ret_val.stream()).collect(Collectors.toList());
		}
		// Caching the results for future use
		try {
			this.mutex.lock();
			autocompleteCache.put(string, ret_val);
		}
		finally {
			this.mutex.unlock();
		}
		return ret_val;
	}
	
	// A utility function to trim lists in a safe way, if the list cannot be trimmed returns the list as is
	private <T> List<T> trimList(List<T> list, int newStartIndex, int newEndIndex) {
		if (list.size() < newEndIndex)
			return list;
		else return list.subList(newStartIndex, newEndIndex);
	}
	
	// Clears the cache
	public void clearCache() {
		try {
			this.mutex.lock();
			autocompleteCache = new HashMap<String, List<String>>();
		}
		finally {
			this.mutex.unlock();
		}
	}
	
	// The class that defines the node type that is used in the tree
	protected class RNode implements Serializable {
		
		private static final long serialVersionUID = 6396691337734909648L;
		
		private TreeMap<String, RNode> children;
		private boolean isEndOfWord;
		
		// A comparator of how to sort the strings in alphabetical order in the map
		private Comparator<String> keyComparator = new Comparator<String>() {
			
			@Override
			public int compare(String o1, String o2) {
				if (o1 == o2) {
			        return 0;
			    }
			    if (o1 == null) {
			        return -1;
			    }
			    if (o2 == null) {
			        return 1;
			    }
			    return o1.compareTo(o2);
			}
			
		};
		
		// Initialize a node with no children
		public RNode(boolean isEndOfWord) {
			this.isEndOfWord = isEndOfWord;
			this.children = new TreeMap<String, RNode>(this.keyComparator);
		}

		// Returns a set of the strings contained in the children of this node
		public Set<String> getChildren() {
			return this.children.keySet();
		}
		
		// Returns the map of the strings and their corresponding nodes
		public TreeMap<String, RNode> getChildrenMap() {
			return this.children;
		}

		// Returns if this is the end of a word
		public boolean isEndOfWord() {
			return isEndOfWord;
		}

		public void setIsEndOfWord(boolean isEndOfWord) {
			this.isEndOfWord = isEndOfWord;
		}
		
		// Adds a given string to the children of this node
		public void addChild(String string, boolean isEndOfWord) {
			this.children.put(string, new RNode(isEndOfWord));
		}
		
		public void addChild(String string, RNode node) {
			this.children.put(string, node); 
		}
		
		public void addChild(String string, RNode node, boolean isEndOfWord) {
			node.setIsEndOfWord(isEndOfWord);
			this.children.put(string, node); 
		}
		
		// Removes a given string to the children of this node
		public void removeChild(String string) {
			this.children.remove(string);
		}
		
		// Returns the corresponding node to a given string key
		public RNode getChildByString(String string) {
			return this.children.get(string);
		}
		
		// Returns true if this node has no children
		public boolean isLeaf() {
			return this.children.isEmpty();
		}
		
	}
	
}
