class DirectoryEntry {
  final String name;
  final String path;

  const DirectoryEntry({required this.name, required this.path});

  factory DirectoryEntry.fromMap(Map<String, dynamic> map) {
    return DirectoryEntry(
      name: map['name'] as String? ?? '',
      path: map['path'] as String? ?? '',
    );
  }
}
