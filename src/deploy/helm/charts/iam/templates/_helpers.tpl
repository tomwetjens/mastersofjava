{{/*
Expand the name of the chart.
*/}}
{{- define "iam.name" -}}
{{- .Chart.Name }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "iam.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "iam.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "iam.labels" -}}
helm.sh/chart: {{ include "iam.chart" . }}
{{ include "iam.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/part-of: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "iam.selectorLabels" -}}
app.kubernetes.io/name: {{ include "iam.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: service
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "iam.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "iam.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "iam.redirectUri" -}}
{{- if .Values.ingress.enabled }}
{{- printf "%s://%s/*" (ternary "https" "http" .Values.ingress.tls.enabled ) .Values.ingress.host }}
{{- else }}
{{- printf "http://localhost:%s/*" .Values.service.port }}
{{- end }}
{{- end }}