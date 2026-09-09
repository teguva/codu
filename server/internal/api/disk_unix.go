package api

import "syscall"

func diskUsage(path string) (map[string]int64, error) {
	var st syscall.Statfs_t
	if err := syscall.Statfs(path, &st); err != nil {
		return nil, err
	}
	bsize := int64(st.Bsize)
	return map[string]int64{
		"totalBytes": int64(st.Blocks) * bsize,
		"freeBytes":  int64(st.Bavail) * bsize,
	}, nil
}
