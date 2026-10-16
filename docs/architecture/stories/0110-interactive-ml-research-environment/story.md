# Story 0110 - Interactive ML Research Environment

## Goal

Provide a lightweight Jupyter exploration layer over the validated Trading OS
research dataset without replacing the deterministic Java boundary or the
tested Python research package.

## Methodology

```text
Java deterministic truth
        -> validated research dataset
        -> structured Python research library
        -> Jupyter exploration
        -> hypothesis
        -> formal experiment
        -> extract reusable logic into tested Python
```

Jupyter is for exploration, visualization, and hypothesis discovery. Structured
Python remains the location for reusable and reproducible research logic. Java
remains the canonical source of deterministic market and research truth.

## Scope

- Add JupyterLab and matplotlib to the existing `research/ml` environment.
- Create `research/notebooks/01_dataset_exploration.ipynb`.
- Load bundles only through the validated 0109A Python boundary.
- Reuse 0109B readiness logic without training a model in notebook 01.
- Explore dataset identity, population, temporal coverage, markets, horizons,
  schema, missingness, distributions, outcomes, and readiness.
- Support empty eligible datasets without crashing.
- Document setup, execution, output policy, and the exploration methodology.

## Constraints

- No absolute machine paths.
- No duplicate dataset validation, experiment, or preprocessing logic.
- No feature leakage, missingness-to-zero conversion, or fabricated horizons.
- No new model, online inference, trading behavior, Java change, or ADR by
  default.
- Notebook outputs remain clearable exploratory artifacts, not runtime evidence.

## Acceptance Criteria

- The notebook runs top-to-bottom after a kernel restart.
- Dataset loading passes through `load_validated_bundle`.
- Dataset identity and temporal/readiness facts are displayed.
- Actual missingness statuses are preserved and visualized.
- Numeric, categorical, boolean, and available outcome fields are explored.
- Empty eligible populations produce an explicit message and skip unsafe plots.
- Notebook 01 does not train Ridge or another model.
- Existing Python tests remain green and notebook structure is validated.
