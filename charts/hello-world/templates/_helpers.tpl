{{- define "hello-world-job.fullname" -}}
{{- printf "%s" .Release.Name -}}
{{- end -}}
