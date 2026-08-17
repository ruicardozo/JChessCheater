# Otimizações — o motor 2,7× mais rápido (17/ago/2026)

> A Fase 8 do **JChessAI** deixou a rede muito mais rápida sem mudar um bit de nenhuma
> jogada (o registro técnico completo está no `otimizacoes.md` de lá). Este arquivo
> documenta o que isso significa **aqui**, no consumidor — e o que mudou neste projeto
> para colher o ganho.

---

## 1. O que o usuário sente

Tempo por lance, por nível, na máquina de 6 núcleos de referência:

| Nível (sims) | 10 | 200 | 800 | 1400 |
|---|---|---|---|---|
| antes | ~0,15 s | ~1,2 s | ~5 s | ~9 s |
| **agora** | **~0,05 s** | **~0,5 s** | **~2 s** | **~3,5 s** |

Na prática: o nível 800 de hoje custa o que o 200 custava — dá para jogar contra os bots
com **4× mais busca pelo mesmo relógio**.

**As jogadas são exatamente as mesmas.** A otimização foi validada por identidade bit a bit
com a rede antiga (4.054.900 floats conferidos), paridade completa com o motor C++ de
referência e partidas gêmeas idênticas. Só o relógio mudou.

---

## 2. O que mudou neste projeto

O motor tem agora dois núcleos aritméticos: o **escalar** (padrão, roda em qualquer lugar)
e o **vetorial** (Vector API/AVX2, ~1,5× mais rápido que o escalar novo), que precisa da
flag `--add-modules jdk.incubator.vector` na linha de comando do Java.

A mudança aqui foi ensinar o `MotorUci` a ligar essa flag **sozinho e com segurança**:

1. **Sonda antes de usar**: um `java --add-modules jdk.incubator.vector -version`
   descartável, uma vez por processo. Se o Java da máquina aceitar, a flag entra no comando
   do motor; se não, o motor sobe sem ela e cai no núcleo escalar sozinho. O cuidado tem
   motivo: um Java sem o módulo se recusaria a *subir* com a flag — e é melhor perder a
   flag que perder o motor.
2. **Diagnóstico visível**: o `MotorUci` captura a linha de carga do motor e a expõe
   (`descricaoDaRede()`). O console do JChessCheater e o `TesteDeFumaca` mostram qual
   núcleo ficou ativo:

   ```
   rede iter_0080.pt carregada em 100 ms — ... — núcleo vetorial (8 lanes)
   ```

3. **Pacote reempacotado** com o `jchessai.jar` novo — lembrando a regra da casa: o JAR é
   artefato do JChessAI e vive aqui por cópia; toda melhoria no motor exige trazer o JAR
   novo para `lib/` e rodar `./empacotar.sh` de novo.

Nada mais foi tocado: a visão, o robô e o laço de jogo já custavam uma fração desprezível
do tempo por lance (o gargalo sempre foi a rede), e a latência do mouse é **deliberada** —
é a humanização, não desperdício.

---

## 3. Como conferir numa máquina nova

```bash
java -cp jchesscheater.jar chesscheater.teste.TesteDeFumaca
```

A seção do motor imprime a linha da rede. `núcleo vetorial (8 lanes)` = ganho completo
ativo; `núcleo escalar` = o Java da máquina não expõe a Vector API (o programa funciona
igual, ~1,5× mais lento). Em ambos os casos, **as jogadas são as mesmas**.
