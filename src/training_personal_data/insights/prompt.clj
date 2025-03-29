(ns training-personal-data.insights.prompt
  (:require [taoensso.timbre :as log]
            [cheshire.core :as json]
            [babashka.http-client :as http]
            [clojure.string :as str]))

(defn build-weekly-insight-prompt
  "Constrói o prompt para análise semanal dos dados de saúde"
  [data]
  (let [has-sleep-data (get-in data [:data :sleep :data_available])
        has-readiness-data (get-in data [:data :readiness :data_available])
        has-activity-data (get-in data [:data :activity :data_available])
        data-status (str "\n\nStatus dos dados:"
                         "\n- Dados de sono: " (if has-sleep-data "DISPONÍVEIS" "AUSENTES")
                         "\n- Dados de prontidão (readiness): " (if has-readiness-data "DISPONÍVEIS" "AUSENTES")
                         "\n- Dados de atividade: " (if has-activity-data "DISPONÍVEIS" "AUSENTES"))]
    (str "Analise meus dados do Oura Ring para a semana de "
         (get-in data [:data :week_range] "")
         " e forneça insights de saúde e recomendações."
         data-status
         "\n\nEsses são os dados disponíveis e suas unidades de medida:"
         "\n- Pontuação de Sono: escala de 0-100"
         "\n- Duração do Sono: em horas (já convertido de segundos)"
         "\n- Qualidade do Sono: percentual de eficiência"
         "\n- Pontuação de Prontidão (Readiness): escala de 0-100"
         "\n- Gasto Calórico: em kcal"
         "\n- Pontuação de Atividade: escala de 0-100"
         "\n\nIMPORTANTE - Formate sua resposta na seguinte estrutura:"
         "\n\n1. Primeiro forneça um resumo dos insights semanais (apenas com base nos dados disponíveis)"
         "\n\n2. Depois crie uma tabela de métricas EXATAMENTE neste formato:"
         "\n\n| Métrica | Valor | Interpretação | Recomendação |"
         "\n|---------|-------|----------------|---------------|"
         "\n| Pontuação de Sono | [valor] | [interpretação] | [recomendação] |"
         "\n| Duração do Sono | [valor] horas | [interpretação] | [recomendação] |"
         "\n... (apenas para os dados que estão disponíveis)"
         "\n\n3. Finalmente, inclua uma seção com o cabeçalho:"
         "\n\n**Cross-Data Insight**: [sua análise que conecta múltiplas métricas disponíveis]"
         "\n\nDados para análise: " (json/generate-string (:data data)))))

(def weekly-insight-system-prompt
  "Você é um analista de saúde especializado em interpretar dados do Oura Ring.
   Forneça insights semanais concisos e recomendações acionáveis com base nos dados.

   IMPORTANTE SOBRE OS DADOS:
   - A duração do sono está em HORAS (já convertida de segundos)
   - A pontuação de sono é numa escala de 0-100, onde:
     * 85-100: Excelente
     * 70-84: Bom
     * 50-69: Regular
     * Abaixo de 50: Ruim
   - A qualidade do sono (eficiência) é um percentual
   - O gasto calórico é em kcal

   TRATAMENTO DE DADOS AUSENTES:
   - Cada categoria (sleep, readiness, activity) tem um campo data_available que indica se existem dados
   - Se data_available for false, isso significa que não há dados suficientes para aquela categoria
   - Você deve adaptar sua análise para considerar apenas os dados disponíveis
   - Não faça suposições sobre dados que não estão disponíveis
   - Se não houver dados de sono, foque nas outras métricas disponíveis
   - Se não houver dados de atividade, foque nas métricas de sono e readiness
   - Se apenas alguns dados estiverem disponíveis, ainda produza insights úteis com o que você tem

   SIGA ESTRITAMENTE o formato solicitado pelo usuário.
   Use português brasileiro para suas respostas.
   Cruze os dados disponíveis para gerar insights relevantes e valiosos.")

(defn call-gpt
  "Chama a API do GPT com os prompts formatados e trata erros"
  [data]
  (try
    (let [api-key (or (System/getenv "OPENAI_API_KEY")
                      (throw (ex-info "Missing OPENAI_API_KEY environment variable" {})))
          structured-prompt (build-weekly-insight-prompt data)
          _ (log/debug {:event :gpt-prompt :prompt structured-prompt})
          response (http/post "https://api.openai.com/v1/chat/completions"
                              {:headers {"Authorization" (str "Bearer " api-key)
                                         "Content-Type" "application/json"}
                               :body (json/generate-string
                                      {:model "gpt-4o"
                                       :messages [{:role "system"
                                                   :content weekly-insight-system-prompt}
                                                  {:role "user"
                                                   :content structured-prompt}]
                                       :temperature 0.7})
                               :throw false})]
      (if (= 200 (:status response))
        (-> response :body json/parse-string (get-in ["choices" 0 "message" "content"]))
        (throw (ex-info "GPT API call failed"
                        {:status (:status response)
                         :body (:body response)}))))
    (catch java.net.ConnectException e
      (log/error {:event :gpt-connection-error :msg "Failed to connect to GPT API" :error (ex-message e)})
      (throw (ex-info "Failed to connect to GPT API. Check your internet connection." {:cause :connection} e)))
    (catch Exception e
      (log/error {:event :gpt-error :msg "Unexpected error calling GPT API" :error (ex-message e)})
      (throw (ex-info (str "GPT API error: " (ex-message e)) {:cause :general} e)))))

(defn extract-gpt-structured-data
  "Extrai os dados estruturados da resposta do GPT"
  [gpt-response]
  (try
    (let [lines (str/split-lines gpt-response)
          ;; Encontrar padrões de tabela (qualquer linha que comece com | e tenha pelo menos um | depois)
          table-rows (->> lines
                          (filter #(re-find #"^\|.*\|" %)))

          ;; Encontrar cross-data insight (mais flexível)
          insight-pattern #"(?i)cross.?data|Insight|Relacionamento|Correlação"
          cross-data-line (->> lines
                               (filter #(re-find insight-pattern %))
                               first)

          ;; Se encontrou uma linha de cross-data, obter o conteúdo completo
          cross-data-insight (when cross-data-line
                               (let [start-idx (.indexOf lines cross-data-line)
                                     following-lines (->> (drop (inc start-idx) lines)
                                                          (take-while #(and (not (str/blank? %))
                                                                            (not (re-find #"^\|" %))
                                                                            (not (re-find #"^#" %))
                                                                            (not (re-find #"\*\*" %)))))
                                     content (str/replace cross-data-line #"^\*\*[^:]*:\s*|\*\*" "")]
                                 (if (seq following-lines)
                                   (str content "\n" (str/join "\n" following-lines))
                                   content)))

          ;; Processar linhas da tabela em estrutura
          table-data (when (>= (count table-rows) 3) ;; Precisa de pelo menos cabeçalho, separador e uma linha de dados
                       (let [headers (-> (first table-rows)
                                         (str/replace #"^\|\s*|\s*\|$" "")
                                         (str/split #"\s*\|\s*"))
                             data-rows (->> (drop 2 table-rows) ;; Pular cabeçalho e linha de separação
                                            (map #(-> %
                                                      (str/replace #"^\|\s*|\s*\|$" "")
                                                      (str/split #"\s*\|\s*"))))]
                         (mapv (fn [row]
                                 ;; Verificar se row tem o mesmo número de colunas que headers
                                 (if (= (count row) (count headers))
                                   (zipmap headers row)
                                   ;; Se não tiver, ajustar para evitar erros
                                   (zipmap (take (count row) headers) row)))
                               data-rows)))]

      ;; Log para debug
      (log/debug {:event :gpt-parse-data
                  :found-table (boolean (seq table-rows))
                  :table-row-count (count table-rows)
                  :found-cross-data (boolean cross-data-line)
                  :tables-headers (when (seq table-data)
                                    (keys (first table-data)))})

      {:gpt_metrics_table (if (seq table-data) table-data [])
       :gpt_cross_data_insight (or cross-data-insight "No cross-data insight found")})
    (catch Exception e
      (log/warn {:event :gpt-parse-error
                 :msg "Failed to parse GPT response structure"
                 :error (ex-message e)
                 :stack-trace (with-out-str (.printStackTrace e))})
      {:gpt_metrics_table []
       :gpt_cross_data_insight (str "Error parsing GPT response: " (ex-message e))})))